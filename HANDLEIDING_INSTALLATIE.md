# Installatie en test - CAW SOS Sprint 1

## 1. Project openen

1. Pak de ZIP uit.
2. Open Android Studio.
3. Kies **File > Open**.
4. Selecteer de map **CAW_SOS_Sprint1**.
5. Wacht tot Gradle klaar is.

## 2. Teamcoördinatoren aanpassen

Open:

`app/src/main/java/be/cawhallevilvoorde/sos/MainActivity.kt`

Zoek:

`teamCoordinators`

Vervang de voorbeeldnummers door echte gsm-nummers.

## 3. Testtoestel voorbereiden

Op de Android-werkgsm:

1. Instellingen > Over de telefoon
2. Tik 7 keer op Buildnummer
3. Ontwikkelaarsopties openen
4. USB-debugging inschakelen

## 4. App starten

1. Sluit de gsm aan via USB.
2. Klik in Android Studio op **Run**.
3. Geef SMS- en locatiemachtigingen.
4. Vul naam, team en Teamcoördinator in.
5. Test eerst met **Test SOS**.

## 5. Wat moet je testen?

- Komt de test-SMS toe bij de Teamcoördinator?
- Staat de naam van de medewerker in de SMS?
- Staat het team in de SMS?
- Staat de GPS-link in de SMS?
- Werkt de rode SOS-knop?
- Werkt shake wanneer de app open staat?

## Let op

In Sprint 1 werkt shake enkel wanneer de app open staat.
In Sprint 2 voegen we de achtergrondservice toe zodat shake ook werkt wanneer het scherm vergrendeld is.
