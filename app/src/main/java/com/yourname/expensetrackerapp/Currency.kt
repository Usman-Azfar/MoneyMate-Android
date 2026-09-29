package com.yourname.expensetrackerapp

data class Currency(val code: String, val displayName: String, val symbol: String)

/** A curated list of common world currencies for the picker — display-symbol only, no live
 *  exchange rates or amount conversion involved anywhere in this app. */
object CurrencyList {
    val all: List<Currency> = listOf(
        Currency("USD", "US Dollar", "$"),
        Currency("EUR", "Euro", "€"),
        Currency("GBP", "British Pound", "£"),
        Currency("JPY", "Japanese Yen", "¥"),
        Currency("INR", "Indian Rupee", "₹"),
        Currency("PKR", "Pakistani Rupee", "₨"),
        Currency("AUD", "Australian Dollar", "$"),
        Currency("CAD", "Canadian Dollar", "$"),
        Currency("CHF", "Swiss Franc", "CHF"),
        Currency("CNY", "Chinese Yuan", "¥"),
        Currency("AED", "UAE Dirham", "د.إ"),
        Currency("SAR", "Saudi Riyal", "﷼"),
        Currency("SGD", "Singapore Dollar", "$"),
        Currency("HKD", "Hong Kong Dollar", "$"),
        Currency("NZD", "New Zealand Dollar", "$"),
        Currency("ZAR", "South African Rand", "R"),
        Currency("BRL", "Brazilian Real", "R$"),
        Currency("MXN", "Mexican Peso", "$"),
        Currency("RUB", "Russian Ruble", "₽"),
        Currency("KRW", "South Korean Won", "₩"),
        Currency("TRY", "Turkish Lira", "₺"),
        Currency("SEK", "Swedish Krona", "kr"),
        Currency("NOK", "Norwegian Krone", "kr"),
        Currency("DKK", "Danish Krone", "kr"),
        Currency("PLN", "Polish Zloty", "zł"),
        Currency("THB", "Thai Baht", "฿"),
        Currency("IDR", "Indonesian Rupiah", "Rp"),
        Currency("MYR", "Malaysian Ringgit", "RM"),
        Currency("PHP", "Philippine Peso", "₱"),
        Currency("VND", "Vietnamese Dong", "₫"),
        Currency("EGP", "Egyptian Pound", "£"),
        Currency("NGN", "Nigerian Naira", "₦"),
        Currency("BDT", "Bangladeshi Taka", "৳"),
        Currency("LKR", "Sri Lankan Rupee", "Rs"),
        Currency("KES", "Kenyan Shilling", "KSh"),
        Currency("QAR", "Qatari Riyal", "﷼"),
        Currency("KWD", "Kuwaiti Dinar", "د.ك")
    )
}
