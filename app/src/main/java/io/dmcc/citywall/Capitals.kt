package io.dmcc.citywall

/**
 * ISO 3166-1 alpha-2 country code -> capital city name, for the optional
 * "capital instead of local town" mode. Returns null for unlisted countries, in
 * which case the caller falls back to the user's real local area. Covers Europe in
 * full plus major countries worldwide; extend [MAP] as needed.
 */
object Capitals {
    fun forCountry(code: String?): String? = code?.let { MAP[it.uppercase()] }

    private val MAP: Map<String, String> = mapOf(
        // Europe
        "AL" to "Tirana", "AD" to "Andorra la Vella", "AT" to "Vienna",
        "BY" to "Minsk", "BE" to "Brussels", "BA" to "Sarajevo", "BG" to "Sofia",
        "HR" to "Zagreb", "CY" to "Nicosia", "CZ" to "Prague", "DK" to "Copenhagen",
        "EE" to "Tallinn", "FI" to "Helsinki", "FR" to "Paris", "DE" to "Berlin",
        "GR" to "Athens", "HU" to "Budapest", "IS" to "Reykjavik", "IE" to "Dublin",
        "IT" to "Rome", "LV" to "Riga", "LI" to "Vaduz", "LT" to "Vilnius",
        "LU" to "Luxembourg", "MT" to "Valletta", "MD" to "Chisinau",
        "MC" to "Monaco", "ME" to "Podgorica", "NL" to "Amsterdam",
        "MK" to "Skopje", "NO" to "Oslo", "PL" to "Warsaw", "PT" to "Lisbon",
        "RO" to "Bucharest", "RU" to "Moscow", "SM" to "San Marino",
        "RS" to "Belgrade", "SK" to "Bratislava", "SI" to "Ljubljana",
        "ES" to "Madrid", "SE" to "Stockholm", "CH" to "Bern", "UA" to "Kyiv",
        "GB" to "London", "VA" to "Vatican City",
        // Americas
        "US" to "Washington", "CA" to "Ottawa", "MX" to "Mexico City",
        "BR" to "Brasilia", "AR" to "Buenos Aires", "CL" to "Santiago",
        "CO" to "Bogota", "PE" to "Lima", "UY" to "Montevideo", "VE" to "Caracas",
        "CU" to "Havana", "JM" to "Kingston",
        // Asia
        "CN" to "Beijing", "JP" to "Tokyo", "KR" to "Seoul", "IN" to "New Delhi",
        "ID" to "Jakarta", "TH" to "Bangkok", "VN" to "Hanoi", "MY" to "Kuala Lumpur",
        "SG" to "Singapore", "PH" to "Manila", "PK" to "Islamabad", "BD" to "Dhaka",
        "AE" to "Abu Dhabi", "SA" to "Riyadh", "IL" to "Jerusalem", "TR" to "Ankara",
        "IR" to "Tehran", "IQ" to "Baghdad", "KZ" to "Astana", "QA" to "Doha",
        // Africa
        "EG" to "Cairo", "ZA" to "Pretoria", "NG" to "Abuja", "KE" to "Nairobi",
        "MA" to "Rabat", "DZ" to "Algiers", "TN" to "Tunis", "ET" to "Addis Ababa",
        "GH" to "Accra", "TZ" to "Dodoma",
        // Oceania
        "AU" to "Canberra", "NZ" to "Wellington", "FJ" to "Suva",
    )
}
