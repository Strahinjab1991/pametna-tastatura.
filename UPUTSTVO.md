# Pametna tastatura v1.0 – kako napraviti APK

1. Raspakuj ZIP (npr. u C:\Projekti\PametnaTastatura).
2. Android Studio → File → Open → izaberi folder PametnaTastatura.
3. Sačekaj da završi "Gradle sync" (prvi put skida alate, par minuta).
   Ako ponudi nadogradnju (Upgrade Assistant / AGP), smiješ prihvatiti.
4. Build → Build App Bundle(s) / APK(s) → Build APK(s).
5. Kad završi, klikni "locate": APK je u app/build/outputs/apk/debug/app-debug.apk.
6. Prebaci APK na telefon (kabl, Viber sebi, Google Drive) i instaliraj
   (telefon pita za dozvolu "instaliranje nepoznatih aplikacija" → dozvoli).

## Uključivanje na telefonu
1. Otvori aplikaciju "Pametna tastatura".
2. Korak 1: uključi je u listi tastatura (Android upozori da tastatura može
   vidjeti tekst – to je standardna poruka za svaku tastaturu; ova nema internet).
3. Korak 2: izaberi je kao trenutnu tastaturu.
4. Probaj u polju "Probaj ovdje": prqvimo, mozemo, telwfonu.

## Kako se koristi
- Dugi pritisak: c → č ć, s → š, z → ž, d → đ, e → €, tačka → ? !, zarez → ; :
- Shift jednom = jedno veliko slovo, dva puta brzo = sva velika.
- Backspace odmah poslije ispravke vraća ono što si otkucao.
  Ako to uradiš 2 puta za istu riječ, ona ide u lični rječnik.
- U traci: lijevo je tvoja riječ „u navodnicima“, sredina je ispravka.
