<?php
require($_SERVER['DOCUMENT_ROOT'] . '/bitrix/modules/main/include/prolog_before.php');

if (!CModule::IncludeModule('iblock')) {
    die(json_encode(['success' => false, 'error' => 'Модуль инфоблоков не загружен']));
}

header('Content-Type: application/json; charset=utf-8');

// === УТИЛИТА: ПОЛУЧЕНИЕ ДЛИТЕЛЬНОСТИ AUDIO ===
function getAudioDuration($audioUrl, $existingDuration = '')
{
    $existingDuration = trim($existingDuration);
    if ($existingDuration !== '') return $existingDuration;

    if (empty($audioUrl)) return '';

    $cacheFile = $_SERVER['DOCUMENT_ROOT'] . '/xml/duration_cache.json';
    $cache = [];
    if (file_exists($cacheFile)) {
        $cache = json_decode(@file_get_contents($cacheFile), true);
        if (!is_array($cache)) $cache = [];
        if (isset($cache[$audioUrl])) return $cache[$audioUrl];
    }

    $fullPath = $_SERVER['DOCUMENT_ROOT'] . $audioUrl;
    $duration = '';

    if (file_exists($fullPath) && strtolower(pathinfo($fullPath, PATHINFO_EXTENSION)) === 'mp3') {
        $out = @shell_exec(
            'ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 '
            . escapeshellarg($fullPath) . ' 2>/dev/null'
        );
        $seconds = floatval(trim((string)$out));
        if ($seconds > 0) {
            $duration = gmdate('H:i:s', (int)$seconds);
        }

        if ($duration === '') {
            $getid3Path = $_SERVER['DOCUMENT_ROOT'] . '/local/php_interface/getid3/getid3.php';
            if (file_exists($getid3Path)) {
                require_once($getid3Path);
                $getID3 = new getID3();
                $fileInfo = $getID3->analyze($fullPath);
                if (!empty($fileInfo['playtime_seconds']) && $fileInfo['playtime_seconds'] > 0) {
                    $duration = gmdate('H:i:s', (int)$fileInfo['playtime_seconds']);
                }
            }
        }
    }

    $cache[$audioUrl] = $duration;
    @file_put_contents($cacheFile, json_encode($cache));

    return $duration;
}

// === УТИЛИТА: ПОЛУЧЕНИЕ ЛОГОТИПА ПОДКАСТА ===
// Качественные логотипы лежат в папке /upload/podcasts_logos/.
// Файл ищется по имени элемента инфоблока (title). Возможные расширения: jpg, jpeg, png, webp.
// Имя нормализуется: нижний регистр, пробелы/спецсимволы -> дефисы. Если файл не найден — пусто.
function getPodcastLogo($title, $fallbackImage = '')
{
    if (empty($title)) return $fallbackImage;

    $logoDir = $_SERVER['DOCUMENT_ROOT'] . '/upload/podcasts_logos/';
    $logoBaseUrl = '/upload/podcasts_logos/';

    // Нормализуем название в имя файла (транслитерацию делаем вручную через простую замену)
    $slug = $title;
    $slug = mb_strtolower($slug, 'UTF-8');

    // Простая транслитерация кириллицы -> латиница
    $trans = [
        'а'=>'a','б'=>'b','в'=>'v','г'=>'g','д'=>'d','е'=>'e','ё'=>'e','ж'=>'zh','з'=>'z',
        'и'=>'i','й'=>'y','к'=>'k','л'=>'l','м'=>'m','н'=>'n','о'=>'o','п'=>'p','р'=>'r',
        'с'=>'s','т'=>'t','у'=>'u','ф'=>'f','х'=>'h','ц'=>'ts','ч'=>'ch','ш'=>'sh','щ'=>'sch',
        'ъ'=>'','ы'=>'y','ь'=>'','э'=>'e','ю'=>'yu','я'=>'ya',' '=>'-'
    ];
    $slug = strtr($slug, $trans);
    $slug = preg_replace('/[^a-z0-9\-_.]/', '-', $slug);
    $slug = preg_replace('/\-{2,}/', '-', $slug);
    $slug = trim($slug, '-');

    if (empty($slug)) return $fallbackImage;

    // Проверяем возможные расширения
    $extensions = ['jpg', 'jpeg', 'png', 'webp'];
    foreach ($extensions as $ext) {
        $filePath = $logoDir . $slug . '.' . $ext;
        if (file_exists($filePath)) {
            return $logoBaseUrl . $slug . '.' . $ext;
        }
    }

    return $fallbackImage;
}

// === УТИЛИТА: ПЕРВАЯ КАРТИНКА ИЗ ТЕКСТА ПОДКАСТА ===
// Если у подкаста нет PREVIEW_PICTURE, берём первую картинку из DETAIL_TEXT (HTML).
function getFirstImageFromText($detailText)
{
    if (empty($detailText)) return '';

    // Ищем <img ... src="..."> в тексте
    if (preg_match('/<img[^>]+src=[\"\']([^\"\']+)[\"\']/i', $detailText, $m)) {
        $src = $m[1];
        // Убираем возможные параметры ресайза Bitrix (?...)
        $src = preg_replace('/\?.*$/', '', $src);
        return $src;
    }

    // Ищем фоновые изображения style="background-image: url('...')"
    if (preg_match('/background-image\s*:\s*url\([\"\']?([^\"\'\)]+)[\"\']?\)/i', $detailText, $m)) {
        return $m[1];
    }

    return '';
}

// === 1. ПОЛУЧАЕМ ВСЕ КАТЕГОРИИ ===
$categories = [];
$resCategories = CIBlockElement::GetList(
    ['SORT' => 'ASC', 'NAME' => 'ASC'],
    ['IBLOCK_ID' => 12, 'ACTIVE' => 'Y'],
    false,
    false,
    ['ID', 'NAME', 'SORT', 'PREVIEW_PICTURE']
);

while ($cat = $resCategories->GetNextElement()) {
    $fields = $cat->GetFields();

    $imageId = $fields['PREVIEW_PICTURE'] ?? '';
    $imageUrl = $imageId ? CFile::GetPath($imageId) : '';

    $categories[] = [
        'id' => $fields['ID'],
        'name' => $fields['NAME'],
        'sort' => $fields['SORT'],
        'imageUrl' => $imageUrl
    ];
}

// === 2. ПОЛУЧАЕМ ПОДКАСТЫ ===
$result = [];

foreach ($categories as $category) {
    $categoryId = $category['id'];
    $categoryName = $category['name'];
    $categoryImage = $category['imageUrl'];

    // Пропускаем категорию infoПОВОД и ТрэндЭ (информационный раздел больше не актуален)
    if (stripos($categoryName, 'infoПОВОД') !== false) {
        continue;
    }

    $podcasts = [];

    $res = CIBlockElement::GetList(
        ['ACTIVE_FROM' => 'DESC'],
        [
            'IBLOCK_ID' => 3,
            'ACTIVE' => 'Y',
            'PROPERTY_TYPE_PODCAST' => $categoryId
        ],
        false,
        false, // выгружаем ВСЕ подкасты без ограничений
        ['ID', 'NAME', 'DETAIL_PAGE_URL', 'PREVIEW_TEXT', 'DETAIL_TEXT', 'PREVIEW_PICTURE', 'PROPERTY_MUSIC', 'PROPERTY_ARTIST', 'PROPERTY_DURATION', 'ACTIVE_FROM']
    );

    while ($el = $res->GetNextElement()) {
        $fields = $el->GetFields();
        $props = $el->GetProperties();

        // === ПОЛУЧЕНИЕ АУДИО ===
        $audioUrl = '';

        if (!empty($props['MUSIC']['VALUE'])) {
            $audioFileId = $props['MUSIC']['VALUE'];
            $audioUrl = CFile::GetPath($audioFileId);
        }

        if (empty($audioUrl)) {
            $resFile = CIBlockElement::GetProperty(
                $fields['IBLOCK_ID'],
                $fields['ID'],
                array(),
                array("CODE" => "MUSIC")
            );
            while ($prop = $resFile->Fetch()) {
                if (!empty($prop['VALUE'])) {
                    $audioUrl = CFile::GetPath($prop['VALUE']);
                    break;
                }
            }
        }

        if (empty($audioUrl)) {
            foreach ($props as $key => $prop) {
                if (is_array($prop) && !empty($prop['VALUE'])) {
                    $testPath = '';
                    if (is_numeric($prop['VALUE'])) {
                        $testPath = CFile::GetPath($prop['VALUE']);
                    } elseif (is_string($prop['VALUE']) && strpos($prop['VALUE'], '.mp3') !== false) {
                        $testPath = $prop['VALUE'];
                    }

                    if (!empty($testPath) && (
                        strpos($testPath, '.mp3') !== false ||
                        strpos($testPath, '.mp4') !== false ||
                        strpos($testPath, 'live.discoveryfm.ru') !== false
                    )) {
                        $audioUrl = $testPath;
                        break;
                    }
                }
            }
        }

        if (empty($audioUrl) && !empty($fields['FILE_NAME'])) {
            $audioUrl = $fields['FILE_NAME'];
        }

        $imageId = $fields['PREVIEW_PICTURE'] ?? '';
        $previewImage = $imageId ? CFile::GetPath($imageId) : '';

        // === КАРТИНКА: логотип из папки → PREVIEW_PICTURE → первая из текста → категория ===
        $imageUrl = getPodcastLogo($fields['NAME'], '');
        if (empty($imageUrl)) {
            $imageUrl = $previewImage;
        }
        if (empty($imageUrl)) {
            $imageUrl = getFirstImageFromText($fields['DETAIL_TEXT'] ?? '');
        }
        if (empty($imageUrl)) {
            $imageUrl = $categoryImage;
        }

        // Нормализуем URL (если начинается с / или относительный — добавляем домен не надо, приложение само подставит)
        if (!empty($imageUrl)) {
            $imageUrl = str_replace('\\/', '/', $imageUrl);
        }

        // === ДЛИТЕЛЬНОСТЬ ===
        $duration = getAudioDuration($audioUrl, $props['DURATION']['VALUE'] ?? '');

        // === ОПИСАНИЕ: PREVIEW_TEXT, если пуст — берём текст из DETAIL_TEXT ===
        $description = trim($fields['PREVIEW_TEXT'] ?? '');
        if (empty($description)) {
            $detailText = $fields['DETAIL_TEXT'] ?? '';
            // Убираем HTML-теги, стили и лишние пробелы
            $detailText = preg_replace('/<style[^>]*>.*?<\/style>/is', ' ', $detailText);
            $detailText = preg_replace('/<script[^>]*>.*?<\/script>/is', ' ', $detailText);
            $description = trim(strip_tags($detailText));
            $description = preg_replace('/\s+/u', ' ', $description);
            // Полный текст без обрезки (в приложении есть прокрутка)
        }

        // === ДЕДУПЛИКАЦИЯ: пропускаем подкаст с уже добавленным аудио-URL ===
        $seenAudioUrls = $seenAudioUrls ?? [];
        if (!empty($audioUrl) && isset($seenAudioUrls[$audioUrl])) {
            continue;
        }
        if (!empty($audioUrl)) {
            $seenAudioUrls[$audioUrl] = true;
        }

        $podcasts[] = [
            'id' => $fields['ID'],
            'title' => $fields['NAME'],
            'description' => $description,
            'artist' => $props['ARTIST']['VALUE'] ?? '',
            'duration' => $duration,
            'audioUrl' => $audioUrl,
            'link' => $fields['DETAIL_PAGE_URL'],
            'imageUrl' => $imageUrl
        ];
    }

    if (!empty($podcasts)) {
        $result[] = [
            'name' => $categoryName,
            'count' => count($podcasts),
            'imageUrl' => $categoryImage,
            'podcasts' => $podcasts
        ];
    }
}

echo json_encode([
    'success' => true,
    'count' => count($result),
    'categories' => $result
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

require($_SERVER['DOCUMENT_ROOT'] . '/bitrix/modules/main/include/epilog_after.php');
?>