# 🤖 ShiftMate — Telegram bot for shift workers

ShiftMate is a Telegram bot that helps shift workers easily track their work schedule.
Designed for rotating weekly shifts (early / night / day), including special night rules.

---

## ✨ Features

- ✅ Choose your current shift:
  - Early (06:00–14:00)
  - Night (22:00–06:00, Sunday starts at 21:00)
  - Day (14:00–22:00)
- 📅 Show schedule for **7 or 14 days**
- 🕒 Correct handling of night shifts:
  - Night week: **Sunday–Friday (6 nights)**
  - Sunday night starts at **21:00**
  - Monday–Friday nights start at **22:00**
- 🔁 Automatic weekly rotation:
Early → Night → Day → Early → ...

- 🧠 Smart logic: Sunday belongs to the next working week
- 💬 Simple Telegram keyboard interface

---

## 🧠 Shift Rules (important)

### Early / Day shifts
- Working days: **Monday–Friday**
- Weekend: **Saturday & Sunday**

### Night shift
- Working days: **Sunday–Friday**
- Saturday is always **off**
- Start time:
- Sunday → **21:00**
- Mon–Fri → **22:00**
- End time: **06:00**

---

## 🚀 How to run locally

### 1️⃣ Create a Telegram bot
- Open Telegram → `@BotFather`
- Run `/newbot`
- Copy the **BOT_TOKEN**
- Note your bot username

---

### 2️⃣ Configure environment variables

#### Option A — Environment variables (recommended)

**Windows (PowerShell):**
```powershell
setx BOT_TOKEN "your_bot_token_here"
setx BOT_USERNAME "your_bot_username"


Linux / macOS:

export BOT_TOKEN=your_bot_token_here
export BOT_USERNAME=your_bot_username

3️⃣ application.yml
telegram:
  bot:
    token: ${BOT_TOKEN}
    username: ${BOT_USERNAME}

4️⃣ Run the bot
mvn spring-boot:run


If everything is correct, you’ll see:

✅ Bot started: @YourBotName

🧪 Usage

Open your bot in Telegram and press Start.

Available buttons:

Early / Night / Day

My shift

Schedule 7 days

Schedule 14 days

Help

Reset settings

🛠 Tech Stack

Java 21

Spring Boot 3

TelegramBots Long Polling API

Maven

📌 Roadmap (planned)

⏰ Shift reminders (notifications)

🗄 PostgreSQL persistence

🌍 Multi-language support (EN / RU / CZ / UA)

📆 Export to calendar (ICS)

👤 Author: evgenshkuropat

Built by Evgen (Shift worker & Java developer)
Project created for real-life usage and learning purposes.

⭐ If you find this project useful — feel free to star the repository!
