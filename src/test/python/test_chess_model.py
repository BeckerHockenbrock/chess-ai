"""Tests for the policy-value network output contract."""

import unittest

import torch

from chess_dataset import INPUT_PLANES, POLICY_SIZE
from chess_model import ChessNet


class ChessNetTest(unittest.TestCase):

    def test_outputs_one_policy_logit_per_encoded_move(self):
        model = ChessNet(dropout=0.0)
        policy_logits, value = model(torch.zeros((2, INPUT_PLANES, 8, 8)))

        self.assertEqual((2, POLICY_SIZE), tuple(policy_logits.shape))
        self.assertEqual((2,), tuple(value.shape))


if __name__ == "__main__":
    unittest.main()
