"""A deliberately small policy-value network for the first training run."""

import torch.nn as nn

from chess_dataset import INPUT_PLANES, POLICY_SIZE


class ChessNet(nn.Module):
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(INPUT_PLANES, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.ReLU(),
        )
        self.policy = nn.Sequential(
            nn.Conv2d(64, 16, kernel_size=1),
            nn.ReLU(),
            nn.Flatten(),
            nn.Linear(16 * 8 * 8, POLICY_SIZE),
        )
        self.value = nn.Sequential(
            nn.Conv2d(64, 1, kernel_size=1),
            nn.ReLU(),
            nn.Flatten(),
            nn.Linear(8 * 8, 64),
            nn.ReLU(),
            nn.Linear(64, 1),
            nn.Tanh(),
        )

    def forward(self, board):
        features = self.features(board)
        return self.policy(features), self.value(features).squeeze(1)
