"""Train the PyTorch Residual Policy-Value model (ResNet) from the SQLite dataset."""

import argparse
import time
from pathlib import Path

import torch
import torch.nn.functional as functional
from torch.utils.data import DataLoader

from chess_dataset import ChessDataset
from chess_model import ChessNet


def get_device():
    if torch.cuda.is_available():
        # ROCm exposes AMD GPUs through PyTorch's CUDA-compatible API.
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def masked_policy_loss(logits, legal_mask, target):
    # Cast to float32 to prevent float16 overflow during mixed-precision and maintain numerical stability
    masked_logits = logits.float().masked_fill(~legal_mask, -1e9)
    return functional.kl_div(functional.log_softmax(masked_logits, dim=1), target.float(),
                             reduction="batchmean")


def calculate_top1_accuracy(logits, legal_mask, target):
    masked_logits = logits.float().masked_fill(~legal_mask, -1e9)
    pred_moves = masked_logits.argmax(dim=1)
    target_moves = target.argmax(dim=1)
    return (pred_moves == target_moves).float().sum().item()


def run_epoch(model, loader, optimizer, scaler, device, is_training=True):
    model.train(is_training)
    total_loss = 0.0
    total_policy_loss = 0.0
    total_value_loss = 0.0
    total_top1_correct = 0
    total_samples = 0

    use_non_blocking = device.type == "cuda"
    device_type = "cuda" if device.type == "cuda" else "cpu"
    use_amp = (device.type == "cuda")

    for batch in loader:
        board = batch["board"].to(device, non_blocking=use_non_blocking)
        legal_mask = batch["legal_mask"].to(device, non_blocking=use_non_blocking)
        policy_target = batch["policy"].to(device, non_blocking=use_non_blocking)
        value_target = batch["value"].to(device, non_blocking=use_non_blocking)
        batch_size = board.size(0)

        with torch.set_grad_enabled(is_training):
            with torch.amp.autocast(device_type=device_type, enabled=use_amp):
                policy_logits, value_prediction = model(board)
                policy_loss = masked_policy_loss(policy_logits, legal_mask, policy_target)
                value_loss = functional.mse_loss(value_prediction, value_target)
                loss = policy_loss + value_loss

            if is_training:
                optimizer.zero_grad(set_to_none=True)
                scaler.scale(loss).backward()
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                scaler.step(optimizer)
                scaler.update()

        total_loss += loss.item() * batch_size
        total_policy_loss += policy_loss.item() * batch_size
        total_value_loss += value_loss.item() * batch_size
        total_top1_correct += calculate_top1_accuracy(policy_logits, legal_mask, policy_target)
        total_samples += batch_size

    n = max(1, total_samples)
    return {
        "loss": total_loss / n,
        "policy_loss": total_policy_loss / n,
        "value_loss": total_value_loss / n,
        "top1_acc": total_top1_correct / n,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", default="data/training.db")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--warmup-epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--lr", type=float, default=0.001)
    parser.add_argument("--lr-min", type=float, default=0.00002)
    parser.add_argument("--blocks", type=int, default=6, help="Number of residual blocks.")
    parser.add_argument("--channels", type=int, default=128, help="Number of convolution channels.")
    parser.add_argument("--dropout", type=float, default=0.1)
    parser.add_argument("--weight-decay", type=float, default=0.0001)
    parser.add_argument("--patience", type=int, default=6,
                        help="Stop after this many validation losses without improvement.")
    parser.add_argument("--no-augment", action="store_true",
                        help="Disable horizontal mirroring data augmentation.")
    parser.add_argument("--output", default="data/chess_model.pt")
    args = parser.parse_args()

    torch.manual_seed(7)
    device = get_device()
    train_data = ChessDataset(args.database, "train", augment=not args.no_augment)
    validation_data = ChessDataset(args.database, "validation", augment=False)
    loader_options = {"num_workers": 0, "pin_memory": device.type == "cuda"}
    train_loader = DataLoader(train_data, batch_size=args.batch_size, shuffle=True, **loader_options)
    validation_loader = DataLoader(validation_data, batch_size=args.batch_size, **loader_options)

    model = ChessNet(num_blocks=args.blocks, channels=args.channels, dropout=args.dropout).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=args.weight_decay)
    scaler = torch.amp.GradScaler("cuda", enabled=(device.type == "cuda"))

    # Learning rate schedule: Linear Warmup followed by Cosine Annealing
    warmup_epochs = min(args.warmup_epochs, args.epochs // 2)
    if warmup_epochs > 0:
        warmup = torch.optim.lr_scheduler.LinearLR(optimizer, start_factor=0.2, total_iters=warmup_epochs)
        cosine = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs - warmup_epochs, eta_min=args.lr_min)
        scheduler = torch.optim.lr_scheduler.SequentialLR(optimizer, schedulers=[warmup, cosine], milestones=[warmup_epochs])
    else:
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=args.lr_min)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    best_validation_loss = float("inf")
    epochs_without_improvement = 0

    aug_str = "with 2x horizontal mirroring" if not args.no_augment else "without augmentation"
    print(f"Training ResNet ({args.blocks} blocks, {args.channels} ch) on {device} with {len(train_data)} samples ({aug_str}) across {args.epochs} epochs.")
    print("=" * 105)
    for epoch in range(1, args.epochs + 1):
        start_time = time.time()
        current_lr = scheduler.get_last_lr()[0]

        train_metrics = run_epoch(model, train_loader, optimizer, scaler, device, is_training=True)
        val_metrics = run_epoch(model, validation_loader, None, scaler, device, is_training=False)
        scheduler.step()
        elapsed = time.time() - start_time

        print(
            f"Epoch {epoch:02d}/{args.epochs:02d} ({elapsed:4.1f}s, lr={current_lr:.6f}) | "
            f"Train: loss={train_metrics['loss']:.4f} (pol={train_metrics['policy_loss']:.4f}, val={train_metrics['value_loss']:.4f}, top1={train_metrics['top1_acc']*100:.1f}%) | "
            f"Val: loss={val_metrics['loss']:.4f} (pol={val_metrics['policy_loss']:.4f}, val={val_metrics['value_loss']:.4f}, top1={val_metrics['top1_acc']*100:.1f}%)"
        )

        if val_metrics["loss"] < best_validation_loss:
            best_validation_loss = val_metrics["loss"]
            epochs_without_improvement = 0
            torch.save({
                "model_state": model.state_dict(),
                "device": str(device),
                "epoch": epoch,
                "validation_loss": val_metrics["loss"],
                "val_top1_acc": val_metrics["top1_acc"],
                "num_blocks": args.blocks,
                "channels": args.channels,
            }, output)
            print(f"  --> Saved new best checkpoint to {output} (val_loss={best_validation_loss:.4f}, top1_acc={val_metrics['top1_acc']*100:.1f}%)")
        else:
            epochs_without_improvement += 1
            if epochs_without_improvement >= args.patience:
                print(f"\nEarly stopping triggered after {args.patience} epochs without validation improvement.")
                break

    print("=" * 105)
    print(f"Training complete! Best validation loss: {best_validation_loss:.4f}")


if __name__ == "__main__":
    main()
