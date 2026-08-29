import unittest

from chess_dataset import (
    POLICY_SIZE,
    fen_to_tensor,
    legal_moves_to_mask,
    mirror_fen,
    mirror_move_uci,
    uci_to_index,
)


class ChessDatasetTest(unittest.TestCase):
    def test_starting_position_has_expected_piece_and_state_planes(self):
        board = fen_to_tensor("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        self.assertEqual((18, 8, 8), tuple(board.shape))
        self.assertEqual(8, int(board[0].sum()))
        self.assertEqual(8, int(board[6].sum()))
        self.assertTrue(bool(board[12, 0, 0]))
        self.assertEqual(0, int(board[17].sum()))

    def test_legal_move_mask_has_one_entry_per_unique_move(self):
        moves = ["e2e4", "g1f3", "b1c3"]
        mask = legal_moves_to_mask(moves)
        self.assertEqual(POLICY_SIZE, len(mask))
        self.assertEqual(3, int(mask.sum()))

    def test_promotion_actions_are_distinct(self):
        self.assertNotEqual(uci_to_index("a7a8q"), uci_to_index("a7a8n"))
        self.assertNotEqual(uci_to_index("a7a8n"), uci_to_index("a7a8r"))

    def test_mirror_move_uci(self):
        self.assertEqual("d2d4", mirror_move_uci("e2e4"))
        self.assertEqual("b1c3", mirror_move_uci("g1f3"))
        self.assertEqual("d7d8q", mirror_move_uci("e7e8q"))
        self.assertEqual("h1h8", mirror_move_uci("a1a8"))

    def test_mirror_fen(self):
        start_fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        mirrored = mirror_fen(start_fen)
        self.assertEqual("rnbkqbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBKQBNR b KQkq d3 0 1", mirrored)


if __name__ == "__main__":
    unittest.main()
