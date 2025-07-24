package raf.console.tg_postman.screens.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class GeoPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val provider = intent.getStringExtra("provider") ?: "YANDEX"
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }



        val html = """
    <!DOCTYPE html>
    <html lang="ru">
    <head>
        <meta charset="utf-8">
        <title>Выбор геопозиции</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">

        <style>
            html, body {
                margin: 0;
                padding: 0;
                height: 100%;
                font-family: sans-serif;
                background-color: #fafafa;
            }

            #map {
                width: 100%;
                height: 100%;
            }

            #markerInfo {
                position: absolute;
                bottom: 16px;
                left: 50%;
                transform: translateX(-50%);
                background: white;
                padding: 12px 16px;
                border-radius: 8px;
                box-shadow: 0 4px 10px rgba(0, 0, 0, 0.2);
                font-size: 14px;
                z-index: 1000;
            }
        </style>

        <script src="https://api-maps.yandex.ru/v3/?apikey=64b5eb7f-0205-4271-bc98-df7e1c88e410&lang=ru_RU" type="text/javascript"></script>
    </head>
    <body>
        <div id="map"></div>
        <div id="markerInfo" style="display: none;"></div>

        <script>
            ymaps.ready(init);

            function init() {
                const map = new ymaps.Map("map", {
                    center: [55.75, 37.61],
                    zoom: 4,
                    controls: ["zoomControl"]
                });

                let placemark = null;

                map.events.add("click", function (e) {
                    const coords = e.get("coords");

                    if (placemark) {
                        placemark.geometry.setCoordinates(coords);
                    } else {
                        placemark = new ymaps.Placemark(coords, {
                            hintContent: "Выбранная точка",
                            balloonContent: "Координаты: ${'$'}{coords[0].toFixed(6)}, ${'$'}{coords[1].toFixed(6)}"
                        }, {
                            preset: 'islands#redDotIcon'
                        });
                        map.geoObjects.add(placemark);
                    }

                    const info = "Выбрано: ${'$'}{coords[0].toFixed(6)}, ${'$'}{coords[1].toFixed(6)}";
                    document.getElementById("markerInfo").innerText = info;
                    document.getElementById("markerInfo").style.display = "block";

                    // Передаём координаты в Android-приложение
                    if (window.Android && Android.setCoordinates) {
                        Android.setCoordinates(coords[0], coords[1]);
                    }
                });
            }
        </script>
    </body>
    </html>
""".trimIndent()




        class JsBridge {
            @JavascriptInterface
            fun setCoordinates(lat: Double, lon: Double) {
                val result = Intent().apply {
                    putExtra("latitude", lat)
                    putExtra("longitude", lon)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }

        webView.addJavascriptInterface(JsBridge(), "Android")
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        setContentView(webView)
    }
}
