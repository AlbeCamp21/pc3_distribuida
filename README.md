# Sistema Banco Shibasito

## Pasos

1. Descargar dependencias (automático)
    ```bash
    setup.bat
    ```

2. Levantar RabbitMQ
    ```bash
    docker compose up -d
    # Panel de administracion: http://localhost:15672 (user: admin / password: admin123)
    ```

3. Compilar
    ```bash
    compilar.bat
    ```

4. Ejecutar (en 3 terminales)
    ```bash
    ejecutar-reniec.bat    # Terminal 1
    ejecutar-banco.bat     # Terminal 2
    ejecutar-cliente.bat   # Terminal 3
    ```