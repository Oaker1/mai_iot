# Рандомный генератор данных для теста MQTT
import time
import random
import json
import paho.mqtt.client as mqtt

# Настройки MQTT
MQTT_BROKER = "localhost"  # IP-адрес или имя хоста брокера MQTT
MQTT_PORT = 1883  # Порт брокера MQTT
MQTT_TOPIC = "mai_iot/test/"  # Топик для отправки данных
INTERVAL = 1  # Интервал отправки данных (в секундах)


# Функция для эмуляции данных батареи
def generate_fake_battery_data():
    return {
        "battery_percentage": random.randint(0, 100),
        "battery_voltage_mv": random.randint(3000, 4200)
    }


# Функция для отправки данных в MQTT
def publish_battery_data():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.connect(MQTT_BROKER, MQTT_PORT, 60)

    while True:
        battery_data = generate_fake_battery_data()
        payload = json.dumps(battery_data)
        client.publish(MQTT_TOPIC, payload)
        print(f"Отправлено: {payload}")

        time.sleep(INTERVAL)


if __name__ == "__main__":
    publish_battery_data()
