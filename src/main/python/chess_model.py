"""A modern Residual Policy-Value Network (ResNet) for chess with GroupNorm."""

import torch.nn as nn

from chess_dataset import INPUT_PLANES, POLICY_SIZE


class ResidualBlock(nn.Module):
    def __init__(self, channels=128, num_groups=8, dropout=0.1):
        super().__init__()
        self.conv1 = nn.Conv2d(channels, channels, kernel_size=3, padding=1)
        self.norm1 = nn.GroupNorm(num_groups, channels)
        self.relu = nn.ReLU(inplace=True)
        self.conv2 = nn.Conv2d(channels, channels, kernel_size=3, padding=1)
        self.norm2 = nn.GroupNorm(num_groups, channels)
        self.dropout = nn.Dropout2d(p=dropout) if dropout > 0 else nn.Identity()

    def forward(self, x):
        residual = x
        out = self.relu(self.norm1(self.conv1(x)))
        out = self.dropout(out)
        out = self.norm2(self.conv2(out))
        return self.relu(out + residual)


class ChessNet(nn.Module):
    def __init__(self, num_blocks=6, channels=128, num_groups=8, dropout=0.1):
        super().__init__()
        self.initial_conv = nn.Sequential(
            nn.Conv2d(INPUT_PLANES, channels, kernel_size=3, padding=1),
            nn.GroupNorm(num_groups, channels),
            nn.ReLU(inplace=True),
        )
        self.res_blocks = nn.ModuleList([
            ResidualBlock(channels=channels, num_groups=num_groups, dropout=dropout)
            for _ in range(num_blocks)
        ])

        # Policy Head: 3x3 conv + 1x1 conv mapping to 73 action planes
        self.policy_head = nn.Sequential(
            nn.Conv2d(channels, channels, kernel_size=3, padding=1),
            nn.GroupNorm(num_groups, channels),
            nn.ReLU(inplace=True),
            nn.Conv2d(channels, 73, kernel_size=1),
        )

        # Value Head: 1x1 conv + dense layers with Tanh activation [-1, 1]
        self.value_head = nn.Sequential(
            nn.Conv2d(channels, 32, kernel_size=1),
            nn.GroupNorm(min(8, 32), 32),
            nn.ReLU(inplace=True),
            nn.Flatten(),
            nn.Linear(32 * 8 * 8, 128),
            nn.ReLU(inplace=True),
            nn.Dropout(p=dropout),
            nn.Linear(128, 1),
            nn.Tanh(),
        )

    def forward(self, board):
        out = self.initial_conv(board)
        for block in self.res_blocks:
            out = block(out)

        policy_maps = self.policy_head(out)
        # Tensor rows run from rank 8 to rank 1, whereas the move encoder
        # enumerates origin squares from a1 to h8.
        policy_logits = policy_maps.flip(dims=(2,)).permute(0, 2, 3, 1).flatten(1)
        value = self.value_head(out).squeeze(1)
        return policy_logits, value
