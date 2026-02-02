# Expense Tracker App

A simple and intuitive Android application built with Kotlin to help users track their daily income and expenses. This app uses `SharedPreferences` and `Gson` for local data persistence, allowing users to manage their finances even after closing the app.

## ✨ Features

- **📊 Dashboard Summary**: View your total balance, total income, and total expenses at a glance.
- **💸 Transaction Management**: Easily add income and expense entries with descriptions, amounts, and categories.
- **🔍 Search & Filter**: Find specific transactions using the real-time search bar or filter by transaction type (Income/Expense).
- **💾 Data Persistence**: Your financial data is automatically saved locally, ensuring it's available every time you open the app.
- **🗑️ Delete Transactions**: Remove unwanted or incorrect entries with a single click (includes balance validation).
- **📉 Empty State Handling**: Clear and helpful messages when no transactions are found or when filters return no results.

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Android XML (Material Component)
- **Data Persistence**: SharedPreferences
- **JSON Serialization**: Gson
- **Architecture**: Activity-based with RecyclerView for transaction lists.

## 🚀 Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- Android SDK 34 or higher
- Kotlin 1.9.0+

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/ExpenseTrackerApp.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync and download dependencies.
4. Run the app on an emulator or a physical device.

## 📸 Screenshots

*(Add your app screenshots here to showcase your work!)*

## 📂 Project Structure

- `MainActivity.kt`: The core logic of the application handling UI and data operations.
- `TransactionAdapter.kt`: Handles the logic for displaying transaction items in the list.
- `res/layout/activity_main.xml`: The main dashboard layout.
- `res/layout/transaction_item.xml`: The layout for individual transaction entries.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
