# Savs Common Economy

A lightweight, standalone economy mod for Minecraft 1.21.10 (Fabric). This mod provides a simple yet robust economy system with JSON persistence, offline player support, and essential commands.

## Features

*   **Economy System**: Tracks player balances using a simple JSON file (`balances.json`).
*   **Offline Support**: Supports payments and administrative actions for offline players who have joined the server at least once.
*   **Configuration**: Customizable default starting balance and currency formatting (symbol, position).
*   **Autocompletion**: Smart tab completion for both online and offline player names.
*   **Selectors**: Basic support for the `@s` (self) selector.

## Commands

### Player Commands
*   `/bal` or `/balance`: Check your own balance.
*   `/bal <player>`: Check another player's balance (Online or Offline).
*   `/pay <player> <amount>`: Pay a specific amount to another player.

### Admin Commands (Level 2+)
*   `/givemoney <player> <amount>`: Add money to a player's account.
*   `/takemoney <player> <amount>`: Remove money from a player's account.
*   `/setmoney <player> <amount>`: Set a player's balance to a specific amount.
*   `/resetmoney <player>`: Reset a player's balance to the default starting value.

## Configuration

The configuration file is located at `config/savs-common-economy/config.json`.

```json
{
  "defaultBalance": 1000,
  "currencySymbol": "$",
  "symbolBeforeAmount": true
}
```

*   `defaultBalance`: The amount of money new players start with (default: 1000).
*   `currencySymbol`: The symbol used for currency (e.g., "$", "€", "Coins").
*   `symbolBeforeAmount`: If true, shows "$100"; if false, shows "100$".

## To-Do / Future Improvements

*   [ ] Full selector support (e.g., `@p`, `@a`, `@r`) for economy commands.
