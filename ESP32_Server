#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <PubSubClient.h>
#include <math.h>

// Wi-Fi настройки
const char* ssid = "ssid"; //тут название вай-фай сети
const char* password = "password"; //тут пароль вай-фай сети

// MQTT настройки
const char* mqtt_server = "192.168.0.151";
const int mqtt_port = 1883;
const char* mqtt_topic = "mai_iot/test/";

// Wi-Fi и MQTT
WiFiClient espClient;
PubSubClient mqttClient(espClient);
AsyncWebServer server(80);

// Структура для фильтра Калмана
typedef struct {
  float q;  // Процессный шум
  float r;  // Шум измерений
  float x;  // Оценка состояния
  float p;  // Ошибка оценки
  float k;  // Коэффициент Калмана
} KalmanFilter;

// Инициализация фильтра Калмана
KalmanFilter currentFilter = {
  .q = 0.01,  // Процессный шум (подбирается экспериментально)
  .r = 10.0,  // Шум измерений (подбирается экспериментально)
  .x = 0,     // Начальная оценка
  .p = 1,     // Начальная ошибка
  .k = 0      // Начальный коэффициент
};

// Применение фильтра Калмана
float applyKalmanFilter(KalmanFilter* kf, float measurement) {
  // Прогнозирование
  kf->p = kf->p + kf->q;
  
  // Обновление
  kf->k = kf->p / (kf->p + kf->r);
  kf->x = kf->x + kf->k * (measurement - kf->x);
  kf->p = (1 - kf->k) * kf->p;
  
  return kf->x;
}

// Подключение к Wi-Fi
void connectToWiFi() {
  WiFi.begin(ssid, password);
  Serial.print("Подключение к Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi подключено. IP: " + WiFi.localIP().toString());
}

// Подключение к MQTT брокеру
void connectToMQTT() {
  while (!mqttClient.connected()) {
    Serial.println("Подключение к MQTT...");
    if (mqttClient.connect("ESP32Client")) {
      Serial.println("MQTT подключено");
    } else {
      Serial.print("Ошибка MQTT: ");
      Serial.print(mqttClient.state());
      delay(2000);
    }
  }
}

// Расчет оставшегося времени работы (в часах)
float calculateRuntime(float chargeCounter, float currentAvg) {
  if (currentAvg >= 0 || chargeCounter <= 0) {
    return 0.0;  // Некорректные значения
  }
  
  // Переводим микроампер-часы в ампер-часы и микроамперы в амперы
  float chargeAh = chargeCounter / 1000000.0;  // µAh -> Ah
  float currentA = -currentAvg / 1000000.0;   // µA -> A (отрицательный ток делаем положительным)
  
  if (currentA < 0.001) {  // Защита от слишком малых токов
    return 999.9;          // Условное "очень большое время"
  }
  
  return chargeAh / currentA;
}

void setup() {
  Serial.begin(9600);
  connectToWiFi();

  mqttClient.setServer(mqtt_server, mqtt_port);
  connectToMQTT();

  // HTTP обработчик
  server.on("/update", HTTP_GET, [](AsyncWebServerRequest *request) {
    String batteryLevelStr = request->getParam("battery_level") ? request->getParam("battery_level")->value() : "0";
    String chargeCounterStr = request->getParam("charge_counter") ? request->getParam("charge_counter")->value() : "0";
    String currentAvgStr = request->getParam("current_avg") ? request->getParam("current_avg")->value() : "0";
    String currentNowStr = request->getParam("current_now") ? request->getParam("current_now")->value() : "0";

    // Преобразуем в числа
    float batteryLevel = batteryLevelStr.toFloat();
    float chargeCounter = chargeCounterStr.toFloat();
    float currentAvg = currentAvgStr.toFloat();
    float currentNow = currentNowStr.toFloat();

    // Применяем фильтр Калмана к среднему току
    float filteredCurrent = applyKalmanFilter(&currentFilter, currentAvg);
    
    // Рассчитываем время работы в часах
    float runtimeHours = calculateRuntime(chargeCounter, filteredCurrent);
    // Составляем JSON строку для отправки по MQTT
    String payload = "{";
    payload += "\"battery_level\": " + String(batteryLevel, 2) + ",";
    payload += "\"charge_counter\": " + String(chargeCounter, 2) + ",";
    payload += "\"current_avg\": " + String(currentAvg, 2) + ",";
    payload += "\"current_now\": " + String(currentNow, 2) + ",";
    payload += "\"current_avg_filtered\": " + String(filteredCurrent, 2) + ",";
    payload += "\"runtime_hours\": " + String(runtimeHours, 2);
    payload += "}";

    // Отправляем по MQTT
    if (mqttClient.connected()) {
      mqttClient.publish(mqtt_topic, payload.c_str());
      Serial.println("MQTT отправлено: " + payload);
    } else {
      Serial.println("MQTT не подключено. Повторное подключение...");
      connectToMQTT();
    }

    request->send(200, "text/plain", "Data received and published via MQTT");
  });

  server.begin();
}

void loop() {
  mqttClient.loop();
}
