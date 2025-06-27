# Используем минимальный образ с OpenJDK 17
FROM openjdk:17-jdk-slim

# Устанавливаем рабочий каталог в контейнере
WORKDIR /app

# Копируем JAR файл в контейнер
COPY PrintTalon-0.0.1-SNAPSHOT.jar app.jar

# Открываем порт приложения
EXPOSE 8084

# Команда для запуска приложения с оптимальными параметрами JVM
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Dsun.java2d.headless=true", "-server", "-jar", "/app/app.jar"]