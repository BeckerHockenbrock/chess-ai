"""A compact policy-value network for chess training."""

import torch.nn as nn

from chess_dataset import INPUT_PLANES, POLICY_SIZE


class ChessNet(nn.Module):
    def __init__(self, dropout=0.1):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(INPUT_PLANES, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.Dropout2d(p=dropout),
        )
        # The policy encoding is 73 move types for each origin square. A 1x1
        # convolution keeps that square-to-move relationship intact and avoids
        # the 4.8M-parameter fully connected policy head used previously.
        self.policy = nn.Conv2d(64, 73, kernel_size=1)
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
        policy_maps = self.policy(features)
        # Tensor rows run from rank 8 to rank 1, whereas the move encoder
        # enumerates origin squares from a1 to h8.
        policy_logits = policy_maps.flip(dims=(2,)).permute(0, 2, 3, 1).flatten(1)
        return policy_logits, self.value(features).squeeze(1)
