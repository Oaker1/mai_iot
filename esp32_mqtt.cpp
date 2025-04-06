#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// Настройки WiFi
const char* ssid = "wifi ssid here";
const char* password = "wifi password here";

// Настройки MQTT
const char* mqttServer = "pc ip if docker used";
const int mqttPort = 1883; //default port number
const char* pubTopic = "mai_iot/test/";  // Обратите внимание на слэш в конце
const long sendInterval = 5000;          // Интервал отправки (5 сек)

// Переменные
WiFiClient espClient;
PubSubClient client(espClient);
unsigned long lastSendTime = 0;

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
}

void setup() {
  Serial.begin(9600);
  while (!Serial) {
    ; // Ждём инициализации последовательного порта
  }

  // Подключение к WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  
  unsigned long wifiTimeout = millis() + 20000;
  while (WiFi.status() != WL_CONNECTED && millis() < wifiTimeout) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nConnected! IP: " + WiFi.localIP().toString());
  } else {
    Serial.println("\nFailed to connect to WiFi");
    while (true);
  }

  // Настройка MQTT
  client.setServer(mqttServer, mqttPort);
  client.setCallback(callback);
  client.setBufferSize(2048);  // Увеличенный буфер для MQTT

  // Подключение к брокеру
  if (client.connect("ESP32Client", NULL, NULL, 0, 0, 0, 0, 1)) {
    Serial.println("Connected to MQTT broker");
    delay(100);  // Короткая задержка после подключения
    client.subscribe("mai_iot/test/");
  } else {
    Serial.print("MQTT connection failed, rc=");
    Serial.println(client.state());
  }
}

void sendSensorData() {
  // Генерация случайных данных как в Python-версии
  int voltage = random(3000, 4200);    // 3000-4200 mV
  int percentage = random(0, 100);     // 0-100%

  // Формирование JSON
  StaticJsonDocument<200> doc;
  doc["battery_voltage_mv"] = voltage;
  doc["battery_percentage"] = percentage;
  
  char jsonBuffer[200];
  serializeJson(doc, jsonBuffer);

  // Отправка с флагом retained
  if (client.publish(pubTopic, jsonBuffer, true)) {
    Serial.print("Data sent: ");
    Serial.println(jsonBuffer);
  } else {
    Serial.println("Failed to send data");
    Serial.printf("MQTT state: %d\n", client.state());
  }
}

void loop() {
  // Поддержание MQTT-соединения
  if (!client.connected()) {
    Serial.println("Reconnecting to MQTT...");
    if (client.connect("ESP32Client", NULL, NULL, 0, 0, 0, 0, 1)) {
      delay(100);
      client.subscribe("mai_iot/test/");
      Serial.println("Reconnected");
    } else {
      Serial.print("Reconnect failed, rc=");
      Serial.println(client.state());
    }
  }
  client.loop();

  // Периодическая отправка данных
  if (millis() - lastSendTime > sendInterval) {
    sendSensorData();
    lastSendTime = millis();
  }

  // Дополнительная диагностика (раз в 10 секунд)
  static unsigned long lastDiagTime = 0;
  if (millis() - lastDiagTime > 10000) {
    Serial.printf("[DIAG] WiFi: %d, MQTT: %d, IP: %s\n",
                 WiFi.status(),
                 client.state(),
                 WiFi.localIP().toString().c_str());
    lastDiagTime = millis();
  }
}
