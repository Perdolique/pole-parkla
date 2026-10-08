import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

type Translation = { en: string; et: string; ru: string }

type Catalog = {
  sourceLanguage?: unknown;
  strings?: Record<string, {
    localizations?: Record<string, {
      stringUnit?: { state?: unknown; value?: unknown };
    }>;
  }>;
}

const translations: Record<string, Translation> = {
  'camera.capture': {
    en: 'Take photo',
    et: 'Tee foto',
    ru: 'Сделать фото'
  },

  'camera.capture.failed': {
    en: 'The photo could not be taken.',
    et: 'Foto tegemine ebaõnnestus.',
    ru: 'Не удалось сделать фото.'
  },

  'camera.capturing': {
    en: 'Taking photo…',
    et: 'Foto tegemine…',
    ru: 'Делаем фото…'
  },

  'camera.gallery': {
    en: 'Choose from photos',
    et: 'Vali fotodest',
    ru: 'Выбрать из галереи'
  },

  'camera.location.accuracy': {
    en: 'Location ready · ±%lld m',
    et: 'Asukoht valmis · ±%lld m',
    ru: 'Геопозиция готова · ±%lld м'
  },

  'camera.location.denied': {
    en: 'Location is off',
    et: 'Asukoht on keelatud',
    ru: 'Геолокация выключена'
  },

  'camera.location.explain': {
    en: 'Allow location while using the app so camera photos can keep a fresh incident point.',
    et: 'Luba asukoht rakenduse kasutamise ajal, et kaamerafotole saaks lisada värske juhtumipunkti.',
    ru: 'Разрешите геолокацию при использовании приложения, чтобы снимки камеры получили свежую точку нарушения.'
  },

  'camera.location.request': {
    en: 'Enable location',
    et: 'Luba asukoht',
    ru: 'Включить геолокацию'
  },

  'camera.location.waiting': {
    en: 'Finding location…',
    et: 'Asukoha otsimine…',
    ru: 'Ищем геопозицию…'
  },

  'camera.limit.replace': {
    en: 'All 3 slots are full. Delete a photo to replace it.',
    et: 'Kõik 3 kohta on täis. Asendamiseks kustuta foto.',
    ru: 'Все 3 места заняты. Удалите фото, чтобы заменить его.'
  },

  'camera.permission.denied': {
    en: 'Camera access is off. Enable it in Settings or choose photos.',
    et: 'Kaamera ligipääs on keelatud. Luba see seadetes või vali fotod.',
    ru: 'Доступ к камере выключен. Разрешите его в настройках или выберите фото.'
  },

  'camera.preview': {
    en: 'Camera preview',
    et: 'Kaamera eelvaade',
    ru: 'Предпросмотр камеры'
  },

  'camera.remove.confirm.body': {
    en: 'The photo will be removed from this report.',
    et: 'Foto eemaldatakse sellest teatest.',
    ru: 'Фотография будет удалена из этого обращения.'
  },

  'camera.remove.confirm.title': {
    en: 'Delete this photo?',
    et: 'Kas kustutada see foto?',
    ru: 'Удалить эту фотографию?'
  },

  'camera.remove.accessibility': {
    en: 'Delete photo %lld',
    et: 'Kustuta foto %lld',
    ru: 'Удалить фото %lld'
  },

  'camera.review': {
    en: 'Review',
    et: 'Kontrolli',
    ru: 'Проверить'
  },

  'camera.slots.hint': {
    en: 'Add up to 3 photos. Delete one to replace it.',
    et: 'Lisa kuni 3 fotot. Asendamiseks kustuta foto.',
    ru: 'Добавьте до 3 фото. Чтобы заменить снимок, удалите его.'
  },

  'camera.unavailable': {
    en: 'Camera is unavailable. You can choose photos instead.',
    et: 'Kaamera pole saadaval. Saad valida fotod.',
    ru: 'Камера недоступна. Можно выбрать фото из галереи.'
  },

  'photo.busy': {
    en: 'Wait for the current photo operation to finish.',
    et: 'Oota, kuni praegune fototoiming lõpeb.',
    ru: 'Дождитесь завершения текущей операции с фото.'
  },

  'common.back': {
    en: 'Back',
    et: 'Tagasi',
    ru: 'Назад'
  },

  'common.cancel': {
    en: 'Cancel',
    et: 'Tühista',
    ru: 'Отмена'
  },

  'common.close': {
    en: 'Close',
    et: 'Sulge',
    ru: 'Закрыть'
  },

  'common.collapsed': {
    en: 'Collapsed',
    et: 'Ahendatud',
    ru: 'Свернуто'
  },

  'common.continue': {
    en: 'Continue',
    et: 'Jätka',
    ru: 'Продолжить'
  },

  'common.current': {
    en: 'Current',
    et: 'Praegune',
    ru: 'Текущий'
  },

  'common.delete': {
    en: 'Delete',
    et: 'Kustuta',
    ru: 'Удалить'
  },

  'common.done': {
    en: 'Done',
    et: 'Valmis',
    ru: 'Готово'
  },

  'common.expanded': {
    en: 'Expanded',
    et: 'Laiendatud',
    ru: 'Развернуто'
  },

  'common.no': {
    en: 'No',
    et: 'Ei',
    ru: 'Нет'
  },

  'common.notice': {
    en: 'Notice',
    et: 'Teade',
    ru: 'Сообщение'
  },

  'common.ok': {
    en: 'OK',
    et: 'OK',
    ru: 'ОК'
  },

  'common.open': {
    en: 'Open',
    et: 'Ava',
    ru: 'Открыть'
  },

  'common.save': {
    en: 'Save',
    et: 'Salvesta',
    ru: 'Сохранить'
  },

  'common.settings': {
    en: 'Settings',
    et: 'Seaded',
    ru: 'Настройки'
  },

  'common.yes': {
    en: 'Yes',
    et: 'Jah',
    ru: 'Да'
  },

  'error.photo.import': {
    en: 'The photo could not be imported.',
    et: 'Foto importimine ebaõnnestus.',
    ru: 'Не удалось импортировать фото.'
  },

  'error.storage': {
    en: 'Local data could not be updated.',
    et: 'Kohalikke andmeid ei saanud uuendada.',
    ru: 'Не удалось обновить локальные данные.'
  },

  'history.empty.body': {
    en: 'New reports will appear here.',
    et: 'Uued teated ilmuvad siia.',
    ru: 'Здесь появятся новые обращения.'
  },

  'history.empty.title': {
    en: 'No reports yet',
    et: 'Teateid veel pole',
    ru: 'Обращений пока нет'
  },

  'history.delete.confirm.body': {
    en: 'The report and its photos will be removed from this iPhone.',
    et: 'Teade ja selle fotod eemaldatakse sellest iPhone\'ist.',
    ru: 'Обращение и его фотографии будут удалены с этого iPhone.'
  },

  'history.delete.confirm.title': {
    en: 'Delete this report?',
    et: 'Kas kustutada see teade?',
    ru: 'Удалить это обращение?'
  },

  'history.location.pending': {
    en: 'Place not confirmed',
    et: 'Asukoht kinnitamata',
    ru: 'Место не подтверждено'
  },

  'history.actions': {
    en: 'More actions',
    et: 'Rohkem toiminguid',
    ru: 'Другие действия'
  },

  'history.new': {
    en: 'New report',
    et: 'Uus teade',
    ru: 'Новое обращение'
  },

  'history.title': {
    en: 'History',
    et: 'Ajalugu',
    ru: 'История'
  },

  'history.unconfirmed': {
    en: 'Plate not confirmed',
    et: 'Number kinnitamata',
    ru: 'Номер не подтверждён'
  },

  'language.system': {
    en: 'System',
    et: 'Süsteem',
    ru: 'Системный'
  },

  'location.unavailable': {
    en: 'Current location is unavailable.',
    et: 'Praegune asukoht pole saadaval.',
    ru: 'Текущее местоположение недоступно.'
  },

  'mail.never.sends': {
    en: 'Pole parkla! prepares a draft. You control editing and sending in the mail app.',
    et: 'Pole parkla! koostab mustandi. Muutmist ja saatmist juhid meilirakenduses sina.',
    ru: 'Pole parkla! готовит черновик. Редактированием и отправкой управляете вы в почтовом приложении.'
  },

  'mail.open': {
    en: 'Open mail app',
    et: 'Ava meilirakendus',
    ru: 'Открыть почту'
  },

  'mail.open.failed': {
    en: 'The mail composer failed to open.',
    et: 'Meilikoostaja avamine ebaõnnestus.',
    ru: 'Не удалось открыть почтовый редактор.'
  },

  'mail.prepare.failed': {
    en: 'The attachments could not be prepared.',
    et: 'Manuste ettevalmistamine ebaõnnestus.',
    ru: 'Не удалось подготовить вложения.'
  },

  'mail.share.confirm.body': {
    en: 'Third-party apps may ignore recipient or subject metadata.',
    et: 'Kolmandad rakendused võivad saaja või teema andmeid eirata.',
    ru: 'Сторонние приложения могут проигнорировать получателя или тему.'
  },

  'mail.share.confirm.title': {
    en: 'Did a mail draft open?',
    et: 'Kas meilimustand avanes?',
    ru: 'Почтовый черновик открылся?'
  },

  'map.accessibility': {
    en: 'Map for selecting the incident point',
    et: 'Kaart juhtumi asukoha valimiseks',
    ru: 'Карта для выбора места нарушения'
  },

  'map.accessibility.east': {
    en: 'Move east',
    et: 'Liiguta itta',
    ru: 'Сдвинуть на восток'
  },

  'map.accessibility.hint': {
    en: 'Use the actions to move the map and select its center.',
    et: 'Kasuta toiminguid kaardi liigutamiseks ja keskpunkti valimiseks.',
    ru: 'Используйте действия, чтобы двигать карту и выбрать точку в центре.'
  },

  'map.accessibility.north': {
    en: 'Move north',
    et: 'Liiguta põhja',
    ru: 'Сдвинуть на север'
  },

  'map.accessibility.select': {
    en: 'Select map center',
    et: 'Vali kaardi keskpunkt',
    ru: 'Выбрать центр карты'
  },

  'map.accessibility.south': {
    en: 'Move south',
    et: 'Liiguta lõunasse',
    ru: 'Сдвинуть на юг'
  },

  'map.accessibility.west': {
    en: 'Move west',
    et: 'Liiguta läände',
    ru: 'Сдвинуть на запад'
  },

  'map.address.failed': {
    en: 'Address lookup failed. You can retry or keep only the selected point.',
    et: 'Aadressiotsing ebaõnnestus. Proovi uuesti või kasuta ainult valitud punkti.',
    ru: 'Не удалось найти адрес. Можно повторить поиск или сохранить только выбранную точку.'
  },

  'map.address.loading': {
    en: 'Looking up nearby addresses…',
    et: 'Lähedaste aadresside otsimine…',
    ru: 'Ищем адреса рядом…'
  },

  'map.failed': {
    en: 'The map could not be loaded.',
    et: 'Kaarti ei saanud laadida.',
    ru: 'Не удалось загрузить карту.'
  },

  'map.retry.address': {
    en: 'Retry address',
    et: 'Proovi aadressi uuesti',
    ru: 'Повторить поиск адреса'
  },

  'map.retry.map': {
    en: 'Retry map',
    et: 'Proovi kaarti uuesti',
    ru: 'Повторить загрузку карты'
  },

  'map.select.hint': {
    en: 'Tap the map to move the selected point.',
    et: 'Valitud punkti liigutamiseks puuduta kaarti.',
    ru: 'Коснитесь карты, чтобы переместить выбранную точку.'
  },

  'map.use.address': {
    en: 'Use address',
    et: 'Kasuta aadressi',
    ru: 'Использовать адрес'
  },

  'map.use.point': {
    en: 'Use point',
    et: 'Kasuta punkti',
    ru: 'Использовать точку'
  },

  'onboarding.intro.body': {
    en: 'Photograph a vehicle blocking a cycle or pedestrian path, verify the details and prepare an editable report.',
    et: 'Pildista jalgratta- või kõnniteed blokeerivat sõidukit, kontrolli andmeid ja koosta muudetav teade.',
    ru: 'Сфотографируйте машину на вело- или пешеходной дорожке, проверьте данные и подготовьте редактируемое обращение.'
  },

  'onboarding.intro.title': {
    en: 'Clear evidence. Your decision.',
    et: 'Selged tõendid. Sinu otsus.',
    ru: 'Чёткие доказательства. Ваше решение.'
  },

  'onboarding.language.body': {
    en: 'You can change the app language later in Settings.',
    et: 'Rakenduse keelt saab hiljem seadetes muuta.',
    ru: 'Язык приложения можно изменить позже в настройках.'
  },

  'onboarding.language.title': {
    en: 'App language',
    et: 'Rakenduse keel',
    ru: 'Язык приложения'
  },

  'onboarding.privacy.open': {
    en: 'How privacy works',
    et: 'Kuidas privaatsus toimib',
    ru: 'Как устроена конфиденциальность'
  },

  'onboarding.profile.body': {
    en: 'Add the name and phone that must appear in every report.',
    et: 'Lisa nimi ja telefon, mis peavad olema igas teates.',
    ru: 'Укажите имя и телефон, которые должны быть в каждом обращении.'
  },

  'onboarding.profile.title': {
    en: 'Report details',
    et: 'Teate andmed',
    ru: 'Данные обращения'
  },

  'onboarding.recipient.body': {
    en: 'This address stays editable for every report.',
    et: 'Seda aadressi saab igas teates muuta.',
    ru: 'Этот адрес можно изменить в каждом обращении.'
  },

  'onboarding.required': {
    en: 'Complete all fields to continue.',
    et: 'Jätkamiseks täida kõik väljad.',
    ru: 'Заполните все поля, чтобы продолжить.'
  },

  'onboarding.safety.body': {
    en: 'Stay aware of traffic. Do not step into the road or confront a driver for a photo.',
    et: 'Jälgi liiklust. Ära astu foto tegemiseks sõiduteele ega astu juhiga vastasseisu.',
    ru: 'Следите за движением. Не выходите на проезжую часть и не вступайте в конфликт с водителем ради фото.'
  },

  'onboarding.safety.title': {
    en: 'Safety first',
    et: 'Ohutus ennekõike',
    ru: 'Сначала безопасность'
  },

  'place.address': {
    en: 'Address',
    et: 'Aadress',
    ru: 'Адрес'
  },

  'place.accuracy': {
    en: 'Accuracy ±%lld m',
    et: 'Täpsus ±%lld m',
    ru: 'Точность ±%lld м'
  },

  'place.confirm': {
    en: 'Confirm place',
    et: 'Kinnita asukoht',
    ru: 'Подтвердить место'
  },

  'place.confirm.hint': {
    en: 'Suggestions are not saved until you confirm.',
    et: 'Soovitusi ei salvestata enne kinnitamist.',
    ru: 'Подсказки не сохраняются до подтверждения.'
  },

  'place.current': {
    en: 'Current',
    et: 'Praegune',
    ru: 'Текущее'
  },

  'place.coordinates': {
    en: 'Coordinates',
    et: 'Koordinaadid',
    ru: 'Координаты'
  },

  'place.from.photo': {
    en: 'From photo',
    et: 'Fotolt',
    ru: 'Из фото'
  },

  'place.invalid.coordinates': {
    en: 'Enter both latitude and longitude within their valid ranges.',
    et: 'Sisesta nii laius- kui ka pikkuskraad lubatud vahemikus.',
    ru: 'Введите широту и долготу целиком и в допустимых пределах.'
  },

  'place.latitude': {
    en: 'Latitude',
    et: 'Laiuskraad',
    ru: 'Широта'
  },

  'place.longitude': {
    en: 'Longitude',
    et: 'Pikkuskraad',
    ru: 'Долгота'
  },

  'place.map': {
    en: 'Refine on map',
    et: 'Täpsusta kaardil',
    ru: 'Уточнить на карте'
  },

  'place.missing.location': {
    en: 'Add an address or a complete coordinate pair.',
    et: 'Lisa aadress või täielik koordinaadipaar.',
    ru: 'Добавьте адрес или полную пару координат.'
  },

  'place.nearby.addresses': {
    en: 'Nearby addresses',
    et: 'Lähedased aadressid',
    ru: 'Адреса рядом'
  },

  'place.nearby.streets': {
    en: 'Nearby streets',
    et: 'Lähedased tänavad',
    ru: 'Улицы рядом'
  },

  'place.time': {
    en: 'Date and time',
    et: 'Kuupäev ja kellaaeg',
    ru: 'Дата и время'
  },

  'place.title': {
    en: 'Place and time',
    et: 'Asukoht ja aeg',
    ru: 'Место и время'
  },

  'privacy.local.body': {
    en: 'Reports, photos and settings stay in this iPhone app container. Plate recognition runs on this iPhone. There is no account, iCloud sync, analytics or advertising.',
    et: 'Teated, fotod ja seaded jäävad selle iPhone\'i rakenduse hoidlasse. Numbrimärgi tuvastus toimub selles iPhone\'is. Kontot, iCloudi sünkroonimist, analüütikat ega reklaame pole.',
    ru: 'Обращения, фото и настройки хранятся в контейнере приложения на этом iPhone. Номер распознаётся на этом iPhone. Нет аккаунта, синхронизации iCloud, аналитики и рекламы.'
  },

  'privacy.local.title': {
    en: 'Local storage',
    et: 'Kohalik salvestus',
    ru: 'Локальное хранение'
  },

  'privacy.location.body': {
    en: 'Location is requested only in the foreground. When a selected photo contains GPS metadata, its exact point is automatically sent to In-AKS over HTTPS for address suggestions. Current location and a chosen map point are sent for the same purpose after you select those actions. Only a place you confirm is stored in the report.',
    et: 'Asukohta küsitakse ainult esiplaanil. Kui valitud foto sisaldab GPS-metaandmeid, saadetakse selle täpne punkt aadressipakkumiste saamiseks automaatselt HTTPS-i kaudu In-AKSile. Praegune asukoht ja valitud kaardipunkt saadetakse samal eesmärgil pärast vastava toimingu valimist. Teatesse salvestatakse ainult sinu kinnitatud koht.',
    ru: 'Геолокация запрашивается только на переднем плане. Если выбранное фото содержит GPS-метаданные, его точные координаты автоматически отправляются по HTTPS в In-AKS для вариантов адреса. Текущая геопозиция и выбранная точка на карте отправляются с той же целью после соответствующего действия. В обращении сохраняется только подтверждённое вами место.'
  },

  'privacy.location.title': {
    en: 'Location',
    et: 'Asukoht',
    ru: 'Местоположение'
  },

  'privacy.mail.body': {
    en: 'The app prepares a draft and attachments but never sends mail. Delete all local data removes reports, photos, temporary copies, settings and map caches.',
    et: 'Rakendus koostab mustandi ja manused, kuid ei saada kirja. Kõigi kohalike andmete kustutamine eemaldab teated, fotod, ajutised koopiad, seaded ja kaardivahemälud.',
    ru: 'Приложение готовит черновик и вложения, но не отправляет письмо. Полное удаление данных удаляет обращения, фото, временные копии, настройки и кэш карты.'
  },

  'privacy.mail.title': {
    en: 'Mail and deletion',
    et: 'Meil ja kustutamine',
    ru: 'Почта и удаление'
  },

  'privacy.map.body': {
    en: 'Opening the map loads OpenFreeMap tiles and exposes the device IP and viewed area to that service. In-AKS receives only the selected coordinates.',
    et: 'Kaardi avamine laadib OpenFreeMapi paanid ning avaldab teenusele seadme IP-aadressi ja nähtava ala. In-AKS saab ainult valitud koordinaadid.',
    ru: 'При открытии карты загружаются тайлы OpenFreeMap; сервис видит IP-адрес и отображаемую область. In-AKS получает только выбранные координаты.'
  },

  'privacy.map.title': {
    en: 'Map and address search',
    et: 'Kaart ja aadressiotsing',
    ru: 'Карта и поиск адреса'
  },

  'privacy.title': {
    en: 'Privacy',
    et: 'Privaatsus',
    ru: 'Конфиденциальность'
  },

  'privacy.full.open': {
    en: 'Read the full privacy policy',
    et: 'Loe täielikku privaatsuspoliitikat',
    ru: 'Открыть полную политику конфиденциальности'
  },

  'recognition.local.failed': {
    en: 'Local recognition failed. You can enter the details manually.',
    et: 'Kohalik tuvastus ebaõnnestus. Andmed saab sisestada käsitsi.',
    ru: 'Локальное распознавание не удалось. Данные можно ввести вручную.'
  },

  'recognition.local.partial': {
    en: 'Some local recognition results were unavailable. Check the suggestions or enter details manually.',
    et: 'Osa kohaliku tuvastuse tulemustest polnud saadaval. Kontrolli pakkumisi või sisesta andmed käsitsi.',
    ru: 'Часть результатов локального распознавания недоступна. Проверьте подсказки или введите данные вручную.'
  },

  'recognition.working': {
    en: 'Recognizing on this iPhone…',
    et: 'Tuvastamine selles iPhone\'is…',
    ru: 'Распознаём на этом iPhone…'
  },

  'report.photo.add': {
    en: 'Add photo',
    et: 'Lisa foto',
    ru: 'Добавить фото'
  },

  'report.photo.delete': {
    en: 'Delete photo',
    et: 'Kustuta foto',
    ru: 'Удалить фото'
  },

  'report.photo.delete.confirm.body': {
    en: 'The original photo and its temporary copies will be removed from this report.',
    et: 'Originaalfoto ja selle ajutised koopiad eemaldatakse sellest teatest.',
    ru: 'Оригинал фото и его временные копии будут удалены из обращения.'
  },

  'report.photo.delete.confirm.title': {
    en: 'Delete this photo?',
    et: 'Kas kustutada see foto?',
    ru: 'Удалить это фото?'
  },

  'report.photos': {
    en: 'Photos',
    et: 'Fotod',
    ru: 'Фото'
  },

  'report.step.place': {
    en: 'Place',
    et: 'Asukoht',
    ru: 'Место'
  },

  'report.step.summary': {
    en: 'Summary',
    et: 'Kokkuvõte',
    ru: 'Итог'
  },

  'report.step.vehicle': {
    en: 'Vehicle',
    et: 'Sõiduk',
    ru: 'Машина'
  },

  'report.step.violation': {
    en: 'Violation',
    et: 'Rikkumine',
    ru: 'Нарушение'
  },

  'report.title': {
    en: 'Prepare report',
    et: 'Koosta teade',
    ru: 'Подготовить обращение'
  },

  'settings.delete.all': {
    en: 'Delete all local data',
    et: 'Kustuta kõik kohalikud andmed',
    ru: 'Удалить все локальные данные'
  },

  'settings.delete.confirm.body': {
    en: 'This permanently removes reports, photos, settings, temporary files and caches from this iPhone.',
    et: 'See eemaldab jäädavalt teated, fotod, seaded, ajutised failid ja vahemälud sellest iPhone\'ist.',
    ru: 'Это навсегда удалит с iPhone обращения, фото, настройки, временные файлы и кэши.'
  },

  'settings.delete.confirm.title': {
    en: 'Delete everything?',
    et: 'Kas kustutada kõik?',
    ru: 'Удалить всё?'
  },

  'settings.feedback': {
    en: 'Bugs and ideas on GitHub',
    et: 'Vead ja ideed GitHubis',
    ru: 'Баги и идеи на GitHub'
  },

  'settings.language': {
    en: 'Language',
    et: 'Keel',
    ru: 'Язык'
  },

  'settings.name': {
    en: 'Name',
    et: 'Nimi',
    ru: 'Имя'
  },

  'settings.phone': {
    en: 'Phone',
    et: 'Telefon',
    ru: 'Телефон'
  },

  'settings.privacy': {
    en: 'Privacy and data',
    et: 'Privaatsus ja andmed',
    ru: 'Конфиденциальность и данные'
  },

  'settings.profile': {
    en: 'Reporter',
    et: 'Teataja',
    ru: 'Заявитель'
  },

  'settings.recipient': {
    en: 'Recipient',
    et: 'Saaja',
    ru: 'Получатель'
  },

  'settings.source': {
    en: 'Source code on GitHub',
    et: 'Lähtekood GitHubis',
    ru: 'Исходники на GitHub'
  },

  'settings.templates': {
    en: 'Templates',
    et: 'Mallid',
    ru: 'Шаблоны'
  },

  'settings.templates.empty': {
    en: 'No custom templates',
    et: 'Kohandatud malle pole',
    ru: 'Своих шаблонов пока нет'
  },

  'settings.title': {
    en: 'Settings',
    et: 'Seaded',
    ru: 'Настройки'
  },

  'settings.version': {
    en: 'Version',
    et: 'Versioon',
    ru: 'Версия'
  },

  'startup.failed': {
    en: 'Could not start Pole parkla!',
    et: 'Pole parkla! käivitamine ebaõnnestus',
    ru: 'Не удалось запустить Pole parkla!'
  },

  'startup.failed.body': {
    en: 'Local data could not be opened. Restart the app and try again.',
    et: 'Kohalikke andmeid ei saanud avada. Taaskäivita rakendus ja proovi uuesti.',
    ru: 'Не удалось открыть локальные данные. Перезапустите приложение и попробуйте снова.'
  },

  'startup.loading': {
    en: 'Opening local data…',
    et: 'Kohalike andmete avamine…',
    ru: 'Открываем локальные данные…'
  },

  'status.draft': {
    en: 'Draft',
    et: 'Mustand',
    ru: 'Черновик'
  },

  'status.handed_off': {
    en: 'Opened in mail',
    et: 'Meilis avatud',
    ru: 'Открыто в почте'
  },

  'status.ready': {
    en: 'Ready',
    et: 'Valmis',
    ru: 'Готово'
  },

  'summary.incomplete': {
    en: 'A few confirmations are missing',
    et: 'Mõned kinnitused puuduvad',
    ru: 'Не хватает нескольких подтверждений'
  },

  'summary.not.ready': {
    en: 'Confirm the vehicle, place and violation first.',
    et: 'Kinnita esmalt sõiduk, asukoht ja rikkumine.',
    ru: 'Сначала подтвердите машину, место и нарушение.'
  },

  'summary.profile.missing': {
    en: 'Add your name and phone in Settings before opening the mail draft.',
    et: 'Lisa enne meilimustandi avamist seadetes oma nimi ja telefon.',
    ru: 'Добавьте имя и телефон в настройках перед открытием почтового черновика.'
  },

  'summary.ready': {
    en: 'Ready to open in mail',
    et: 'Valmis meilis avamiseks',
    ru: 'Готово к открытию в почте'
  },

  'summary.sender': {
    en: 'Sender',
    et: 'Saatja',
    ru: 'Отправитель'
  },

  'summary.subject': {
    en: 'Subject',
    et: 'Teema',
    ru: 'Тема'
  },

  'summary.vehicle': {
    en: 'Make and model',
    et: 'Mark ja mudel',
    ru: 'Марка и модель'
  },

  'summary.violation': {
    en: 'Violation',
    et: 'Rikkumine',
    ru: 'Нарушение'
  },

  'tab.camera': {
    en: 'Camera',
    et: 'Kaamera',
    ru: 'Камера'
  },

  'tab.history': {
    en: 'History',
    et: 'Ajalugu',
    ru: 'История'
  },

  'tab.settings': {
    en: 'Settings',
    et: 'Seaded',
    ru: 'Настройки'
  },

  'vehicle.candidate': {
    en: 'Plate candidate',
    et: 'Numbrimärgi kandidaat',
    ru: 'Вариант номера'
  },

  'vehicle.confirm.hint': {
    en: 'Recognition never confirms the vehicle for you. Check and save it yourself.',
    et: 'Tuvastus ei kinnita sõidukit sinu eest. Kontrolli ja salvesta ise.',
    ru: 'Распознавание не подтверждает машину за вас. Проверьте и сохраните данные сами.'
  },

  'vehicle.make': {
    en: 'Make (optional)',
    et: 'Mark (valikuline)',
    ru: 'Марка (необязательно)'
  },

  'vehicle.model': {
    en: 'Model (optional)',
    et: 'Mudel (valikuline)',
    ru: 'Модель (необязательно)'
  },

  'vehicle.optional.details': {
    en: 'Optional vehicle details',
    et: 'Sõiduki valikulised andmed',
    ru: 'Дополнительные сведения'
  },

  'vehicle.plate': {
    en: 'Registration number',
    et: 'Registreerimisnumber',
    ru: 'Регистрационный номер'
  },

  'vehicle.plate.evidence': {
    en: 'Plate evidence',
    et: 'Numbrimärgi tõend',
    ru: 'Номер на фото'
  },

  'vehicle.recognized.variants': {
    en: 'Recognized variants',
    et: 'Tuvastatud variandid',
    ru: 'Распознанные варианты'
  },

  'vehicle.save.confirm': {
    en: 'Save and confirm vehicle',
    et: 'Salvesta ja kinnita sõiduk',
    ru: 'Сохранить и подтвердить машину'
  },

  'vehicle.title': {
    en: 'Check the vehicle',
    et: 'Kontrolli sõidukit',
    ru: 'Проверьте машину'
  },

  'violation.custom.add': {
    en: 'Add custom template',
    et: 'Lisa kohandatud mall',
    ru: 'Добавить свой шаблон'
  },

  'violation.custom.description.et': {
    en: 'Estonian description',
    et: 'Eestikeelne kirjeldus',
    ru: 'Описание на эстонском'
  },

  'violation.custom.delete.confirm.body': {
    en: 'Reports using it will return to draft until you choose another violation.',
    et: 'Seda kasutavad teated muutuvad mustandiks, kuni valid teise rikkumise.',
    ru: 'Обращения с этим шаблоном вернутся в черновики, пока вы не выберете другое нарушение.'
  },

  'violation.custom.delete.confirm.title': {
    en: 'Delete this template?',
    et: 'Kas kustutada see mall?',
    ru: 'Удалить этот шаблон?'
  },

  'violation.custom.edit': {
    en: 'Edit template',
    et: 'Muuda malli',
    ru: 'Изменить шаблон'
  },

  'violation.custom.name': {
    en: 'Template name',
    et: 'Malli nimi',
    ru: 'Название шаблона'
  },

  'violation.cycle': {
    en: 'Parked on a cycle path',
    et: 'Pargitud jalgrattateele',
    ru: 'Парковка на велодорожке'
  },

  'violation.pedestrian': {
    en: 'Parked on a pedestrian path',
    et: 'Pargitud kõnniteele',
    ru: 'Парковка на пешеходной дорожке'
  },

  'violation.tap.hint': {
    en: 'Choosing saves immediately and continues.',
    et: 'Valik salvestub kohe ja jätkab.',
    ru: 'Выбор сохранится сразу, и мастер продолжит.'
  },

  'violation.title': {
    en: 'Choose the violation',
    et: 'Vali rikkumine',
    ru: 'Выберите нарушение'
  }
}

const stringEntries = Object.entries(translations).map(([key, value]) => {
  const localizationEntries = Object.entries(value).map(([language, text]) => [language, {
    stringUnit: {
      state: 'translated',
      value: text
    }
  }])

  return [key, {
    extractionState: 'manual',
    localizations: Object.fromEntries(localizationEntries)
  }]
})

const strings = Object.fromEntries(stringEntries)
const output = resolve('ios/PoleParkla/Resources/Localizable.xcstrings')

const rendered = `${JSON.stringify({
  sourceLanguage: 'en',
  strings,
  version: '1.0'
}, null, 2)}\n`

const requiredLanguages = ['en', 'et', 'ru']

function validateCatalog(catalog: Catalog, path: string) {
  if (catalog.sourceLanguage !== 'en') throw new Error(`${path}: sourceLanguage must be en`)

  for (const [key, entry] of Object.entries(catalog.strings ?? {})) {
    const languages = Object.keys(entry.localizations ?? {}).sort()

    if (languages.join(',') !== requiredLanguages.join(',')) {
      throw new Error(`${path}: ${key} must contain en, et and ru`)
    }

    for (const language of requiredLanguages) {
      const unit = entry.localizations[language]?.stringUnit

      if (unit?.state !== 'translated' || typeof unit.value !== 'string' || unit.value.trim() === '') {
        throw new Error(`${path}: ${key} has an incomplete ${language} translation`)
      }
    }
  }
}

validateCatalog(JSON.parse(rendered), output)

const infoPlistCatalogPath = resolve('ios/PoleParkla/Resources/InfoPlist.xcstrings')

validateCatalog(JSON.parse(readFileSync(infoPlistCatalogPath, 'utf8')), infoPlistCatalogPath)

if (process.argv.includes('--check')) {
  if (readFileSync(output, 'utf8') !== rendered) {
    throw new Error(`${output} is stale; run node ios/scripts/generate-localizations.ts`)
  }

  process.exit(0)
}

mkdirSync(dirname(output), { recursive: true })
writeFileSync(output, rendered)
