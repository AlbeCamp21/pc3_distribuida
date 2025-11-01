import com.rabbitmq.client.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SistemaRENIEC {
    
    private static final String HOST = "localhost";
    private static final String QUEUE_VALIDACION_REQUEST = "banco.validacion.request";
    private static final String QUEUE_VALIDACION_RESPONSE = "banco.validacion.response";
    private static final String RUTA_BD2 = "databases/bd2-reniec/personas.csv";
    
    private Connection conexion;
    private Channel canal;
    
    public static void main(String[] args) {
        SistemaRENIEC sistema = new SistemaRENIEC();
        sistema.iniciar();
    }
    
    public void iniciar() {
        try {
            System.out.println("SISTEMA RENIEC INICIANDO");
            
            // Conectar a RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(HOST);
            factory.setUsername("admin");
            factory.setPassword("admin123");
            
            conexion = factory.newConnection();
            canal = conexion.createChannel();
            
            // Declarar colas
            canal.queueDeclare(QUEUE_VALIDACION_REQUEST, true, false, false, null);
            canal.queueDeclare(QUEUE_VALIDACION_RESPONSE, true, false, false, null);
            
            System.out.println("Conectado a RabbitMQ");
            System.out.println("Escuchando solicitudes de validacion...");
            
            // Consumir mensajes
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String mensaje = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("\nSolicitud recibida: " + mensaje);
                
                // Procesar validacion
                String respuesta = validarPersona(mensaje);
                
                // Enviar respuesta
                canal.basicPublish("", QUEUE_VALIDACION_RESPONSE, null, 
                    respuesta.getBytes(StandardCharsets.UTF_8));
                
                System.out.println("Respuesta enviada: " + respuesta);
            };
            
            canal.basicConsume(QUEUE_VALIDACION_REQUEST, true, deliverCallback, consumerTag -> {});
            
            System.out.println("Sistema RENIEC activo. Presione CTRL+C para salir.");
            
        } catch (Exception e) {
            System.err.println("Error en Sistema RENIEC: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Valida si una persona existe en BD2
    private String validarPersona(String dni) {
        try {
            File archivo = new File(RUTA_BD2);
            BufferedReader br = new BufferedReader(new FileReader(archivo));
            
            String linea;
            boolean primeraLinea = true;
            
            while ((linea = br.readLine()) != null) {
                // Saltar encabezado
                if (primeraLinea) {
                    primeraLinea = false;
                    continue;
                }
                
                String[] datos = linea.split(",");
                
                if (datos.length >= 7 && datos[0].trim().equals(dni.trim())) {
                    // Persona encontrada
                    br.close();
                    return construirRespuesta(true, datos);
                }
            }
            
            br.close();
            // Persona no encontrada
            return construirRespuesta(false, null);
            
        } catch (IOException e) {
            System.err.println("Error al leer BD2: " + e.getMessage());
            return "ERROR|No se pudo acceder a la base de datos RENIEC";
        }
    }
    
    // Construye la respuesta de validacion
    private String construirRespuesta(boolean existe, String[] datos) {
        if (!existe) {
            return "NO_EXISTE|DNI no encontrado en RENIEC";
        }
        
        // Formato: EXISTE|DNI|apellido_pat|apellido_mat|nombres|fecha_naci|sexo|direccion
        return String.format("EXISTE|%s|%s|%s|%s|%s|%s|%s",
            datos[0].trim(),  // DNI
            datos[1].trim(),  // apellido paterno
            datos[2].trim(),  // apellido materno
            datos[3].trim(),  // nombres
            datos[4].trim(),  // fecha nacimiento
            datos[5].trim(),  // sexo
            datos[6].trim()   // direccion
        );
    }
    
    // Cerrar conexiones
    public void cerrar() {
        try {
            if (canal != null && canal.isOpen()) {
                canal.close();
            }
            if (conexion != null && conexion.isOpen()) {
                conexion.close();
            }
            System.out.println("Conexiones cerradas");
        } catch (Exception e) {
            System.err.println("Error al cerrar conexiones: " + e.getMessage());
        }
    }
}
