---
title: Privaatsuspoliitika
description: Kuidas Pole parkla töötleb fotosid, asukohta, teateid, meilirakendusse edastamist ja valikulise pilvetuvastuse andmeid.
lastUpdated: 2026-08-23
---

## Vastutav töötleja ja kontakt

Pole parkla arendaja ja käitaja on **Perdolique**. Privaatsuse või selle poliitika kohta saab küsimusi saata aadressile [hello@poleparkla.ee](mailto:hello@poleparkla.ee).

See poliitika hõlmab Pole parkla Androidi rakendust ja veebisaiti `poleparkla.ee`. Pole parklal ei ole kasutajakontosid, reklaame, analüütikat, krahhiaruannete SDK-d ega taustsünkroonimist.

## Seadmesse salvestatavad andmed

Rakendus võib salvestada teataja nime ja telefoninumbri, valitud teate saaja, teated, kohandatud probleemimallid, fotod, asukoha- ja ajaandmed ning numbrimärgi tuvastuse tähelepanekud. Tähelepanek võib sisaldada tuvastatud väärtust, lähtefotot, tehnilisi kindlusnäitajaid ja numbrimärgi väljalõike koordinaate fotol.

Need andmed jäävad rakenduse privaatsesse salvestusruumi, kuni kasutaja kustutab üksiku teate või valib **Kustuta kõik kohalikud andmed**. Rakenduse andmete Androidi varundamine ja seadmetevaheline ülekandmine on välja lülitatud.

Kui kasutaja seadistab valikulise pilvetuvastuse pääsutõendi, krüpteeritakse see AES-GCM-iga, kasutades Android Keystore'i mitteeksporditavat võtit. Pääsutõend seotakse seadistatud HTTPS-i lähtekohaga. Lähtekoha muutmine tühistab varasema nõusoleku ja välistab pääsutõendi saatmise teise lähtekohta. Teenusepakkuja API-võtmeid rakenduses ei hoita.

## Töötlemine seadmes

Pole parkla käitab valitud originaalfotodel rakendusse lisatud ML Kiti tekstituvastust, YOLOv9-T numbrimärgi detektorit ja CCT-S numbrimärgi tuvastajat. Mudelid töötavad ONNX Runtime'i kaudu kohapeal. Selle töötlemise jaoks fotosid üles ei laadita.

Pärast foto valimist loeb rakendus foto tegemise aega ja GPS-metaandmeid. Asukohta küsitakse ainult siis, kui rakendus on avatud ning kasutaja koostab või muudab teadet. Kasutaja võib asukohaloast keelduda ning koha ja aja käsitsi sisestada.

## Asukoht, aadressid ja kaart

Kui teade saab koordinaadid esiplaani asukohast, foto metaandmetest või kaardivalikust, teisendab Pole parkla punkti seadmes WGS84-st L-EST97-ks ning saadab täpse rist- ja põhjakordinaadi HTTPS-i kaudu Maa- ja Ruumiameti [In-AKS aadressiteenusele](https://geoportaal.maaamet.ee/est/teenused/integreeritav-aadressiotsing-in-ads-p504.html). Eesmärk on pakkuda asukoha täpsusest tuletatud raadiuses lähedal asuvaid tänavaid ja hooneid.

Aadressikandidaate hoitakse mälus. Teatesse salvestatakse ainult aadress, mille kasutaja selgesõnaliselt kinnitab. Teenuse hiline vastus ei saa asendada päringu ajal muudetud koordinaati ega aadressi. Fotosid, registreerimisnumbrit, teataja andmeid, saajat ega kirja teksti In-AKSile ei saadeta.

Valiku **Täpsusta kaardil** avamine laadib OpenStreetMapi andmetel põhineva OpenFreeMapi stiili ja kaardipaanid. [OpenFreeMapile](https://openfreemap.org/) tehtavad päringud avaldavad teenusele seadme IP-aadressi ja kaardil vaadatud ala. Kaardi puudutamisel saadetakse valitud koordinaadid In-AKSile lähedaste aadresside leidmiseks. Kaardi võib vahele jätta ja aadressi saab alati käsitsi sisestada.

## Valikuline pilvetuvastus

Pilvetuvastus käivitub ainult pärast teenusepakkuja nupu vajutamist ja selle pakkuja teavitusega nõustumist. Pole parkla loob põhifotost uue EXIF-andmeteta JPEG-faili, piirab pikema külje 2048 pikslini ja mahu 1 MB-ni ning saadab ainult:

- valitud teenusepakkuja, Workers AI või OpenAI;
- põhifotost loodud JPEG-faili.

Teataja profiili, saajat, aadressi, koordinaate, teisi fotosid ega kirja teksti ei lisata. Pole parkla ei proovi ebaõnnestunud päringut automaatselt teise teenusepakkujaga uuesti.

Kaasnev Cloudflare Worker on olekuta ega kirjuta pilte D1-sse, KV-sse, R2-sse, järjekordadesse ega muusse rakenduse salvestusruumi. Nende päringute AI Gateway logimine ja vahemälu on välja lülitatud. Workeri automaatsed käivituslogid ja trace'id on välja lülitatud; kohandatud vealogid ei sisalda pilte ega mudeli viipasid.

Valitud AI-teenusepakkuja töötleb saadetud pilti siiski oma tingimuste ja kontoseadete alusel. Vaata [Workers AI andmekasutust](https://developers.cloudflare.com/workers-ai/platform/data-usage/) ja [OpenAI API andmehalduse tingimusi](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint). OpenAI puhul ei lülita `store: false` üksi välja kuritarvituste seireks säilitamist. Zero Data Retention või Modified Abuse Monitoring on OpenAI konto seadistus, mida Pole parkla ei saa kontrollida.

## Meilirakendusse edastamine

Pole parkla ei saada teadet ise. Rakendus koostab saaja, teema, sisu ja ajutised manusekoopiad ning avab kasutaja valitud meilirakenduse. Väline rakendus otsustab, kas kirja muudetakse, saadetakse või jäetakse saatmata.

Manusekoopia maht on kuni 2 MB ja foto pikem külg kuni 2560 pikslit. Mõlema piiri sisse jääv foto kopeeritakse muutmata ja võib seetõttu säilitada kõik originaalfailis olevad metaandmed. Liiga suure JPEG-foto uuesti tihendamisel säilitatakse selle EXIF/APP1 segmendid täielikult kuni 256 KB metaandmete piirini. Kui see piir ületatakse või teisendatakse muu toetatud pildivorming, säilitab koopia võimaluse korral ainult GPS-koordinaadid, pildistamiskuupäevad, kuvamissuuna ning kaamera tootja ja mudeli. Android annab valitud meilirakendusele ajutise lugemisõiguse `FileProvider`i kaudu. Pole parkla salvestab ainult meilirakenduse avamise ega saa teada, kas kiri saadeti.

## Google Play arvustus

Pärast esimest lõpetatud meilirakendusse edastamist võib Pole parkla kasutaja rakendusse naasmisel paluda Google Playl näidata süsteemset arvustuskaarti. Google Play otsustab, kas kaart kuvatakse, ning töötleb hinnangut ja arvustuse teksti. Pole parkla ei saa hinnangut ega arvustust lugeda ning hoiab ainult kohalikku seadistust, mis väldib uut taotlust.

## Säilitamine ja kustutamine

Teate kustutamine eemaldab ka selle salvestatud fotod, numbrimärgi tähelepanekud ja ajutised manusekoopiad. **Kustuta kõik kohalikud andmed** katkestab lõpetamata kirjutamised ja aadressipäringud ning eemaldab teated, fotod, kohandatud mallid, teataja profiili, seaded, keelevaliku, krüpteeritud pilvetuvastuse pääsutõendi, ajutised failid, mälus olevad aadressipakkumised ja MapLibre'i kaardipaanide vahemälu.

In-AKSile, OpenFreeMapile, AI-teenusepakkujale, meilirakendusele, Google Playle või muule välisele teenusele saadetud andmetele kehtivad pärast seadmest lahkumist vastava teenuse säilitamis- ja kustutamisreeglid.

## Veebisait ja e-post

Pole parkla veebisait luuakse staatilise HTML-ina ja seda teenindab Cloudflare. Saidi kood ei lisa analüütikat, küpsiseid, vorme, reklaame ega brauseri salvestusruumi. Saidi edastamiseks saab Cloudflare vältimatult võrgupäringu andmeid, näiteks IP-aadressi, ning rakendab oma [privaatsuspoliitikat](https://www.cloudflare.com/privacypolicy/).

Kontaktilink avab külastaja meilirakenduse; veebisait ei saada vormi. Aadressile `hello@poleparkla.ee` saadetud kirju töötleb meiliteenuse osutaja. Kirjavahetust hoitakse ainult nii kaua, kui on vaja päringule vastamiseks, vajalike dokumentide säilitamiseks või kehtivate õiguslike kohustuste täitmiseks. Saatja võib taotleda kustutamist samale aadressile kirjutades.

## Turvalisus ja kasutaja valikud

Pole parkla võrgupäringud kasutavad HTTPS-i. Kohalikud andmed asuvad rakenduse privaatses salvestusruumis, Androidi varundamine on välja lülitatud ja valikuline pilvetuvastuse pääsutõend on kaitstud Android Keystore'i krüpteeringuga. Ükski salvestus- ega edastusviis ei saa tagada absoluutset turvalisust.

Kasutaja võib asukohaloast keelduda, kaarti mitte kasutada, pilvetuvastusest loobuda, loodud teadet enne meilirakendusse edastamist muuta, kirja mustandi kustutada, üksikuid teateid eemaldada või kõik kohalikud rakenduse andmed kustutada. Privaatsusküsimused ja e-kirjavahetust puudutavad taotlused võib saata aadressile [hello@poleparkla.ee](mailto:hello@poleparkla.ee).

## Poliitika muudatused

Lehe ülaosas on viimase uuenduse kuupäev. Andmetöötluse olulised muudatused kajastatakse siin ja rakenduse privaatsusteabes enne muudetud käitumise avaldamist.
