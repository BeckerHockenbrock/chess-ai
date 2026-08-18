"""Train the first PyTorch policy-value model from the SQLite dataset."""

import argparse
from pathlib import Path

import torch
import torch.nn.functional as functional
from torch.utils.data import DataLoader

from chess_dataset import ChessDataset
from chess_model import ChessNet


def get_device():
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def masked_policy_loss(logits, legal_mask, target):
    # Use a finite value: KL-divergence can produce NaN from 0 * infinity.
    masked_logits = logits.masked_fill(~legal_mask, -1_000_000_000.0)
    return functional.kl_div(functional.log_softmax(masked_logits, dim=1), target,
                             reduction="batchmean")


def run_epoch(model, loader, optimizer, device):
    training = optimizer is not None
    model.train(training)
    total_loss = 0.0
    for batch in loader:
        board = batch["board"].to(device)
        legal_mask = batch["legal_mask"].to(device)
        policy_target = batch["policy"].to(device)
        value_target = batch["value"].to(device)

        with torch.set_grad_enabled(training):
            policy_logits, value_prediction = model(board)
            policy_loss = masked_policy_loss(policy_logits, legal_mask, policy_target)
            value_loss = functional.mse_loss(value_prediction, value_target)
            loss = policy_loss + value_loss
            if training:
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
        total_loss += loss.item() * board.size(0)
    return total_loss / len(loader.dataset)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", default="data/training.db")
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--patience", type=int, default=3,
                        help="Stop after this many validation losses without improvement.")
    parser.add_argument("--output", default="data/chess_model.pt")
    args = parser.parse_args()

    torch.manual_seed(7)
    device = get_device()
    train_data = ChessDataset(args.database, "train")
    validation_data = ChessDataset(args.database, "validation")
    train_loader = DataLoader(train_data, batch_size=args.batch_size, shuffle=True, num_workers=0)
    validation_loader = DataLoader(validation_data, batch_size=args.batch_size, num_workers=0)
    model = ChessNet().to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=0.001)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    best_validation_loss = float("inf")
    epochs_without_improvement = 0

    print("Training on", device, "with", len(train_data), "positions.")
    for epoch in range(1, args.epochs + 1):
        train_loss = run_epoch(model, train_loader, optimizer, device)
        validation_loss = run_epoch(model, validation_loader, None, device)
        print(f"Epoch {epoch:02d}: train={train_loss:.4f} validation={validation_loss:.4f}")
        if validation_loss < best_validation_loss:
            best_validation_loss = validation_loss
            epochs_without_improvement = 0
            torch.save({
                "model_state": model.state_dict(),
                "device": str(device),
                "epoch": epoch,
                "validation_loss": validation_loss,
            }, output)
            print("Saved best checkpoint to", output)
        else:
            epochs_without_improvement += 1
            if epochs_without_improvement >= args.patience:
                print("Stopped early after", args.patience, "epochs without validation improvement.")
                break


if __name__ == "__main__":
    main()
