import unittest
from pathlib import Path

from model_service import ModelService


class ModelServiceTest(unittest.TestCase):
    def setUp(self):
        model_path = Path("data/chess_model.pt")
        if not model_path.exists():
            self.skipTest("Model file data/chess_model.pt does not exist.")
        self.service = ModelService(str(model_path), device_name="cpu")

    def test_predict_selects_legal_move(self):
        fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        legal_moves = ["e2e4", "d2d4", "g1f3", "b1c3", "c2c4"]
        best_move, value, scored = self.service.predict(fen, legal_moves)

        self.assertIn(best_move, legal_moves)
        self.assertEqual(len(scored), len(legal_moves))
        self.assertTrue(-1.0 <= value <= 1.0)
        self.assertEqual(best_move, scored[0][0])

    def test_predict_empty_legal_moves(self):
        fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        best_move, value, scored = self.service.predict(fen, [])
        self.assertEqual(best_move, "(none)")
        self.assertEqual(scored, [])


if __name__ == "__main__":
    unittest.main()
