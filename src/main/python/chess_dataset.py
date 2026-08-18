"""Turns the collected chess positions into PyTorch training examples."""

import json
import math
import sqlite3

import torch
from torch.utils.data import Dataset


INPUT_PLANES = 18
POLICY_SIZE = 4672

PIECE_PLANES = {
    "P": 0, "N": 1, "B": 2, "R": 3, "Q": 4, "K": 5,
    "p": 6, "n": 7, "b": 8, "r": 9, "q": 10, "k": 11,
}
QUEEN_DIRECTIONS = (
    (0, 1), (0, -1), (1, 0), (-1, 0),
    (1, 1), (1, -1), (-1, 1), (-1, -1),
)
KNIGHT_DIRECTIONS = (
    (1, 2), (2, 1), (2, -1), (1, -2),
    (-1, -2), (-2, -1), (-2, 1), (-1, 2),
)


class ChessDataset(Dataset):
    """A small in-memory Dataset for the project's SQLite training database."""

    def __init__(self, database_path, split):
        with sqlite3.connect(database_path) as connection:
            self.rows = connection.execute(
                "SELECT fen, legal_moves_json, teacher_policy_json, value_cp, value_mate "
                "FROM positions WHERE split = ? ORDER BY id", (split,)
            ).fetchall()
        if not self.rows:
            raise ValueError("No positions found for split: " + split)

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        fen, legal_json, teacher_json, value_cp, value_mate = self.rows[index]
        legal_moves = json.loads(legal_json)
        teacher_moves = json.loads(teacher_json)
        return {
            "board": fen_to_tensor(fen),
            "legal_mask": legal_moves_to_mask(legal_moves),
            "policy": teacher_policy_to_tensor(teacher_moves),
            "value": torch.tensor(score_to_value(value_cp, value_mate), dtype=torch.float32),
        }


def fen_to_tensor(fen):
    """Encode FEN as 18 planes: pieces, turn, castling rights, en-passant."""
    board, turn, castling, en_passant, *_ = fen.split()
    tensor = torch.zeros((INPUT_PLANES, 8, 8), dtype=torch.float32)

    for row, rank in enumerate(board.split("/")):
        column = 0
        for symbol in rank:
            if symbol.isdigit():
                column += int(symbol)
            else:
                tensor[PIECE_PLANES[symbol], row, column] = 1.0
                column += 1

    if turn == "w":
        tensor[12].fill_(1.0)
    for plane, right in enumerate("KQkq", start=13):
        if right in castling:
            tensor[plane].fill_(1.0)
    if en_passant != "-":
        file_index = ord(en_passant[0]) - ord("a")
        row = 8 - int(en_passant[1])
        tensor[17, row, file_index] = 1.0
    return tensor


def legal_moves_to_mask(moves):
    mask = torch.zeros(POLICY_SIZE, dtype=torch.bool)
    for move in moves:
        mask[uci_to_index(move)] = True
    return mask


def teacher_policy_to_tensor(analyses, temperature=100.0):
    """Convert Stockfish MultiPV scores into a probability distribution."""
    scores = [analysis_score(analysis) for analysis in analyses]
    largest = max(scores)
    weights = [math.exp((score - largest) / temperature) for score in scores]
    total = sum(weights)
    target = torch.zeros(POLICY_SIZE, dtype=torch.float32)
    for analysis, weight in zip(analyses, weights):
        target[uci_to_index(analysis["move"])] += weight / total
    return target


def analysis_score(analysis):
    if analysis["scoreMate"] is not None:
        return 10000 if analysis["scoreMate"] > 0 else -10000
    return analysis["scoreCp"]


def score_to_value(score_cp, score_mate):
    if score_mate is not None:
        return 1.0 if score_mate > 0 else -1.0
    return math.tanh(score_cp / 600.0)


def uci_to_index(move):
    """Map a UCI move to one of 64 origin squares times 73 move types.

    Queen promotions use their normal one-square queen direction. Knight, bishop,
    and rook promotions use the final nine move types.
    """
    from_file = ord(move[0]) - ord("a")
    from_rank = int(move[1]) - 1
    to_file = ord(move[2]) - ord("a")
    to_rank = int(move[3]) - 1
    origin = from_rank * 8 + from_file
    file_delta = to_file - from_file
    rank_delta = to_rank - from_rank

    if len(move) == 5 and move[4] in "nbr":
        piece = "nbr".index(move[4])
        direction = file_delta + 1
        if direction not in (0, 1, 2):
            raise ValueError("Invalid promotion move: " + move)
        return origin * 73 + 64 + piece * 3 + direction

    if (abs(file_delta), abs(rank_delta)) in ((1, 2), (2, 1)):
        return origin * 73 + 56 + KNIGHT_DIRECTIONS.index((file_delta, rank_delta))

    distance = max(abs(file_delta), abs(rank_delta))
    direction = (file_delta // distance, rank_delta // distance)
    return origin * 73 + QUEEN_DIRECTIONS.index(direction) * 7 + distance - 1
