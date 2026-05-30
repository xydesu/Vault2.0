# Vault 2.0

<p align="center">
  <img src="https://i.imgur.com/HxgguHP.png" alt="Vault 2.0" width="720" />
</p>

[![CI](https://github.com/shalom25/Vault2.0/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/shalom25/Vault2.0/actions/workflows/build.yml) [![Release](https://img.shields.io/github/v/release/shalom25/Vault2.0?display_name=tag)](https://github.com/shalom25/Vault2.0/releases/latest) [![Downloads](https://img.shields.io/github/downloads/shalom25/Vault2.0/total)](https://github.com/shalom25/Vault2.0/releases)

What is Vault2.0?
Vault2.0 is an economy plugin that registers a Bukkit Economy service compatible with the Vault API, allowing other plugins (shops, ranks, etc.) to use money without depending on the original Vault.jar. It includes menus, pay/charge flows, loans, and safe configuration and message reloads`.


##What is Vault2.0?
Vault2.0 is an economy plugin that registers a Bukkit Economy service compatible with the Vault API, allowing other plugins (shops, ranks, etc.) to use money without depending on the original Vault.jar. It includes menus, pay/charge flows, loans, and safe configuration and message reloads

## ━━━━━━━━━━IMPORTANT━━━━━━━━━━

Do NOT run this plugin alongside the original Vault.jar (same plugin name). Remove Vault.jar before starting

## ━━━━━━━━━━Features━━━━━━━━━━

Internal economy with persistence (file storage; optional MySQL).
/pay with GUI and per-player submenu (pay, charge, view balance, loans).
Loans with GUI wizard (amounts via chat only).
Defaulted effects configurable (slowness/fatigue, etc.) when a loan defaults.
/vault main menu (Pay / Loan / Settings / Reload / Update).
Safe reload: /vault reload updates config.yml and messages_*.yml without overwriting your values.
Multi-language: en, es, fr, de, nl, pt, ru, zh_TW, hi.

##━━━━━━━━━━ Installation ━━━━━━━━━━

Copy the .jar file to the plugins folder on your server. Start the server to generate the configuration.
MySQL compatibility: compatibility with MySQL, allowing users to integrate and manage databases more efficiently

##━━━━━━━interactive menu━━━━━━━━

Submenu:
1: pay send money to a player
2: balance shows the player's money
3: Charge sends an interactive message to the player with the designated amount (clicking on the message automatically sends the money without using commands).

##━━━━━━━━Loan System━━━━━━━━
The loan system helps manage the game's finances. Players can apply for loans, manage payments, and view their financial status.
Request a Loan
To request a loan, open the menu with `/loan` or `/prestamo` and select **Request**. Specify the amount and, if there are installments, also the amount of each one.
Money Delivery
Upon confirmation, the money is instantly deposited, and the loan is recorded as "active."
Automatic Collection
The system attempts to collect installments automatically. If there's enough balance, it deducts from the balance.
View Status
In the menu, the **Status** option shows the outstanding balance and the next payment date.
Pay Manually
You can use the **Pay** option to pay part or all of the loan at any time.
debt
If there's not enough balance to collect, the loan goes into debt. This can cause negative effects until the debt is settled.
This system simplifies financial management in the game, offering control and dynamism.


## Download
- Latest release: https://github.com/shalom25/Vault2.0/releases/latest

## Configuration
- File: `plugins/Vault 2.0/config.yml` (auto-generated on first start via `saveDefaultConfig()`).
- Storage:
  - `storage.use_mysql: false` — por defecto usa `balances.yml`.
  - MySQL block (comentado en el archivo): `# MySQL storage (set use_mysql: true to enable)`
    - `storage.mysql.host`
    - `storage.mysql.port`
    - `storage.mysql.database`
    - `storage.mysql.username`
    - `storage.mysql.password`
    - `storage.mysql.pool_size`
- Import Essentials (opcional):
  - `import.essentials.enabled: false`
  - `import.essentials.replace: false` (si `true`, reemplaza balances existentes; si `false`, hace merge)
- Offline UUID fallback:
  - `offline-uuid-fallback` (default `true`). En `online-mode=true` se ignora; en `offline-mode=true` permite operar con nombres no vistos usando UUIDs determinísticos.

## PlaceholderAPI
- Si `PlaceholderAPI` está instalado, la expansión se registra automáticamente.
- Placeholders disponibles:
  - `%vault_balance% / %vault2_balance%`
  - `%vault_balance_formatted%`
  - `%vault_balance_<player>%`
  - `%vault_balance_formatted_<player>%`
  - `%vault_eco_balance% / %vault2_eco_balance%`
  - `%vault_eco_balance_fixed%`
  - `%vault_eco_balance_commas%`
  - `%vault_currency_symbol% / %vault2_currency_symbol%`
  - `%vault_balance_<player>%`
  - `%vault_top%`
  - `%vault_top_1%`
