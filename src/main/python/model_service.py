"""A persistent inference service bridge for ChessNet."""

import argparse
import os
import sys
from pathlib import Path

import torch

from chess_dataset import fen_to_tensor, uci_to_index
from chess_model import ChessNet


def get_device(requested=None):
    if requested:
        return torch.device(requested)
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


class ModelService:
    def __init__(self, model_path, device_name=None):
        self.model_path = Path(model_path)
        if not self.model_path.exists():
            raise FileNotFoundError(f"Model file not found: {self.model_path}")

        self.device = get_device(device_name)
        checkpoint = torch.load(self.model_path, map_location=self.device)
        if isinstance(checkpoint, dict) and "model_state" in checkpoint:
            blocks = checkpoint.get("num_blocks", 6)
            channels = checkpoint.get("channels", 128)
            try:
                self.model = ChessNet(num_blocks=blocks, channels=channels).to(self.device)
                self.model.load_state_dict(checkpoint["model_state"])
            except Exception:
                self.model = ChessNet().to(self.device)
                self.model.load_state_dict(checkpoint["model_state"], strict=False)
            self.epoch = checkpoint.get("epoch", "?")
            self.val_loss = checkpoint.get("validation_loss", "?")
        else:
            self.model = ChessNet().to(self.device)
            self.model.load_state_dict(checkpoint, strict=False)
            self.epoch = "?"
            self.val_loss = "?"

        self.model.eval()

    def evaluate_raw(self, fen):
        tensor = fen_to_tensor(fen).unsqueeze(0).to(self.device)
        with torch.no_grad():
            policy_logits, value = self.model(tensor)
        return policy_logits[0].cpu(), value.item()

    def predict(self, fen, legal_moves):
        if not legal_moves:
            try:
                _, value = self.evaluate_raw(fen)
            except Exception:
                value = 0.0
            return "(none)", value, []

        policy_logits, value = self.evaluate_raw(fen)

        scored_moves = []
        for move in legal_moves:
            try:
                idx = uci_to_index(move)
                score = policy_logits[idx].item()
                scored_moves.append((move, score))
            except Exception:
                scored_moves.append((move, -9999.0))

        scored_moves.sort(key=lambda x: x[1], reverse=True)
        best_move = scored_moves[0][0]

        return best_move, value, scored_moves


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=os.getenv("CHESS_MODEL_PATH", "data/chess_model.pt"))
    parser.add_argument("--device", default=None)
    args = parser.parse_args()

    try:
        service = ModelService(args.model, args.device)
    except Exception as exc:
        print(f"ERROR: Failed to initialize model service: {exc}", flush=True)
        sys.exit(1)

    print(f"READY device={service.device} epoch={service.epoch} val_loss={service.val_loss}", flush=True)

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        if line in ("QUIT", "quit"):
            break

        if line in ("READY?", "ready?", "isready"):
            print("READY", flush=True)
            continue

        if line in ("INFO", "info"):
            print(f"INFO model={service.model_path} device={service.device} epoch={service.epoch} val_loss={service.val_loss}", flush=True)
            continue

        if line.startswith("PREDICT ") or line.startswith("predict "):
            moves_str = ""
            if "|" in line:
                cmd_fen, moves_part = line.split("|", 1)
                fen = cmd_fen.split(" ", 1)[1].strip()
                moves_str = moves_part.strip()
            elif " -- " in line:
                cmd_fen, moves_part = line.split(" -- ", 1)
                fen = cmd_fen.split(" ", 1)[1].strip()
                moves_str = moves_part.strip()
            else:
                tokens = line.split()
                if len(tokens) >= 8:
                    fen = " ".join(tokens[1:7])
                    moves_str = tokens[7]
                elif len(tokens) >= 3:
                    fen = tokens[1]
                    moves_str = tokens[2]
                else:
                    fen = tokens[1]
                    moves_str = ""

            legal_moves = [m.strip() for m in moves_str.split(",") if m.strip()] if moves_str else []
            try:
                best_move, value, scored = service.predict(fen, legal_moves)
                scores_str = ",".join(f"{m}:{s:.4f}" for m, s in scored[:5])
                print(f"BESTMOVE {best_move} VALUE {value:.4f} TOP {scores_str}", flush=True)
            except Exception as e:
                print(f"ERROR {e}", flush=True)
            continue

        if line.startswith("EVALUATE ") or line.startswith("evaluate "):
            parts = line.split(" ", 1)
            fen = parts[1].strip()
            try:
                _, value = service.evaluate_raw(fen)
                print(f"VALUE {value:.4f}", flush=True)
            except Exception as e:
                print(f"ERROR {e}", flush=True)
            continue

        print(f"ERROR Unknown command: {line}", flush=True)


if __name__ == "__main__":
    main()
