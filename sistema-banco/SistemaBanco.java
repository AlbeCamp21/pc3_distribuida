import com.rabbitmq.client.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class SistemaBanco {
    
    private static final String HOST = "localhost";
    private static final String QUEUE_VALIDACION_REQUEST = "banco.validacion.request";
    private static final String QUEUE_VALIDACION_RESPONSE = "banco.validacion.response";
    private static final String QUEUE_TRANSACCIONES = "banco.transacciones";
    private static final String QUEUE_PRESTAMOS_SOLICITUD = "banco.prestamos.solicitud";
    private static final String QUEUE_PRESTAMOS_RESPUESTA = "banco.prestamos.respuesta";
    private static final String QUEUE_CONSULTAS = "banco.consultas";
    private static final String QUEUE_CONSULTAS_RESPUESTA = "banco.consultas.respuesta";
    
    private static final String RUTA_CUENTAS = "databases/bd1-banco/cuentas.txt";
    private static final String RUTA_PRESTAMOS = "databases/bd1-banco/prestamos.txt";
    private static final String RUTA_TRANSACCIONES = "databases/bd1-banco/transacciones.txt";
    
    private Connection conexion;
    private Channel canal;
    
    // Locks para sincronizacion con hilos
    private final Object lockCuentas = new Object();
    private final Object lockPrestamos = new Object();
    private final Object lockTransacciones = new Object();
    
    public static void main(String[] args) {
        SistemaBanco sistema = new SistemaBanco();
        sistema.iniciar();
    }
    
    public void iniciar() {
        try {
            System.out.println("SISTEMA BANCO SHIBASITO INICIANDO");
            
            // Conectar a RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(HOST);
            factory.setUsername("admin");
            factory.setPassword("admin123");
            
            conexion = factory.newConnection();
            canal = conexion.createChannel();
            
            // Declarar todas las colas
            canal.queueDeclare(QUEUE_TRANSACCIONES, true, false, false, null);
            canal.queueDeclare(QUEUE_PRESTAMOS_SOLICITUD, true, false, false, null);
            canal.queueDeclare(QUEUE_PRESTAMOS_RESPUESTA, true, false, false, null);
            canal.queueDeclare(QUEUE_CONSULTAS, true, false, false, null);
            canal.queueDeclare(QUEUE_CONSULTAS_RESPUESTA, true, false, false, null);
            
            System.out.println("Conectado a RabbitMQ");
            System.out.println("Banco Shibasito activo. Escuchando solicitudes...");
            
            // Iniciar hilos para procesar diferentes tipos de solicitudes
            iniciarProcesadorTransacciones();
            iniciarProcesadorPrestamos();
            iniciarProcesadorConsultas();
            
            System.out.println("Todos los procesadores activos. Presione CTRL+C para salir.");
            
        } catch (Exception e) {
            System.err.println("Error en Sistema Banco: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Hilo para procesar transacciones
    private void iniciarProcesadorTransacciones() throws IOException {
        DeliverCallback callback = (consumerTag, delivery) -> {
            new Thread(() -> {
                try {
                    String mensaje = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    System.out.println("\n[TRANSACCION] Solicitud: " + mensaje);
                    
                    String respuesta = procesarTransaccion(mensaje);
                    System.out.println("[TRANSACCION] Respuesta: " + respuesta);
                    
                } catch (Exception e) {
                    System.err.println("Error procesando transaccion: " + e.getMessage());
                }
            }).start();
        };
        
        canal.basicConsume(QUEUE_TRANSACCIONES, true, callback, consumerTag -> {});
        System.out.println("Procesador de Transacciones iniciado");
    }
    
    // Hilo para procesar prestamos
    private void iniciarProcesadorPrestamos() throws IOException {
        DeliverCallback callback = (consumerTag, delivery) -> {
            new Thread(() -> {
                try {
                    String mensaje = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    System.out.println("\n[PRESTAMO] Solicitud: " + mensaje);
                    
                    String respuesta = procesarPrestamo(mensaje);
                    
                    // Enviar respuesta
                    canal.basicPublish("", QUEUE_PRESTAMOS_RESPUESTA, null, 
                        respuesta.getBytes(StandardCharsets.UTF_8));
                    
                    System.out.println("[PRESTAMO] Respuesta: " + respuesta);
                    
                } catch (Exception e) {
                    System.err.println("Error procesando prestamo: " + e.getMessage());
                }
            }).start();
        };
        
        canal.basicConsume(QUEUE_PRESTAMOS_SOLICITUD, true, callback, consumerTag -> {});
        System.out.println("Procesador de Prestamos iniciado");
    }
    
    // Hilo para procesar consultas
    private void iniciarProcesadorConsultas() throws IOException {
        DeliverCallback callback = (consumerTag, delivery) -> {
            new Thread(() -> {
                try {
                    String mensaje = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    System.out.println("\n[CONSULTA] Solicitud: " + mensaje);
                    
                    String respuesta = procesarConsulta(mensaje);
                    
                    // Enviar respuesta
                    canal.basicPublish("", QUEUE_CONSULTAS_RESPUESTA, null, 
                        respuesta.getBytes(StandardCharsets.UTF_8));
                    
                    System.out.println("[CONSULTA] Respuesta enviada");
                    
                } catch (Exception e) {
                    System.err.println("Error procesando consulta: " + e.getMessage());
                }
            }).start();
        };
        
        canal.basicConsume(QUEUE_CONSULTAS, true, callback, consumerTag -> {});
        System.out.println("Procesador de Consultas iniciado");
    }
    
    // Procesa una transaccion (deposito o retiro)
    private String procesarTransaccion(String mensaje) {
        // Formato: TRANSACCION|id_cuenta|tipo|monto|usuario
        String[] partes = mensaje.split("\\|");
        
        if (partes.length < 5) {
            return "ERROR|Formato de transaccion invalido";
        }
        
        String idCuenta = partes[1];
        String tipo = partes[2];
        double monto = Double.parseDouble(partes[3]);
        String usuario = partes[4];
        
        synchronized (lockCuentas) {
            try {
                // Verificar que la cuenta existe y obtener saldo actual
                String[] datosAnteriores = buscarCuenta(idCuenta);
                if (datosAnteriores == null) {
                    return "ERROR|Cuenta no encontrada";
                }
                
                double saldoActual = Double.parseDouble(datosAnteriores[2]);
                double nuevoSaldo;
                
                if (tipo.equalsIgnoreCase("deposito")) {
                    nuevoSaldo = saldoActual + monto;
                } else if (tipo.equalsIgnoreCase("retiro")) {
                    if (saldoActual < monto) {
                        return "ERROR|Saldo insuficiente";
                    }
                    nuevoSaldo = saldoActual - monto;
                } else {
                    return "ERROR|Tipo de transaccion invalido";
                }
                
                // Actualizar saldo en BD1
                actualizarSaldoCuenta(idCuenta, nuevoSaldo);
                
                // Registrar transaccion
                synchronized (lockTransacciones) {
                    registrarTransaccion(idCuenta, tipo, monto, usuario);
                }
                
                return String.format("EXITO|Transaccion exitosa. Nuevo saldo: %.2f", nuevoSaldo);
                
            } catch (Exception e) {
                return "ERROR|" + e.getMessage();
            }
        }
    }
    
    // Procesa solicitud de prestamo
    private String procesarPrestamo(String mensaje) {
        // Formato: PRESTAMO|dni|id_cliente|monto
        String[] partes = mensaje.split("\\|");
        
        if (partes.length < 4) {
            return "ERROR|Formato de prestamo invalido";
        }
        
        String dni = partes[1];
        String idCliente = partes[2];
        double monto = Double.parseDouble(partes[3]);
        
        try {
            // Validar identidad en RENIEC
            String validacion = validarEnRENIEC(dni);
            
            if (!validacion.startsWith("EXISTE")) {
                return "ERROR|DNI no valido en RENIEC";
            }
            
            // Verificar que el cliente tenga cuenta
            String[] datosCuenta = buscarCuentaPorCliente(idCliente);
            if (datosCuenta == null) {
                return "ERROR|Cliente no tiene cuenta en el banco";
            }
            
            // Verificar prestamos activos
            if (tienePrestamosActivos(idCliente)) {
                return "ERROR|Cliente ya tiene prestamos activos";
            }
            
            // Aprobar y registrar prestamo
            synchronized (lockPrestamos) {
                String idPrestamo = generarIdPrestamo();
                registrarPrestamo(idPrestamo, idCliente, monto);
                
                return String.format("APROBADO|Prestamo aprobado. ID: %s Monto: %.2f", 
                    idPrestamo, monto);
            }
            
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }
    
    // Procesa consultas de informacion
    private String procesarConsulta(String mensaje) {
        // Formato: CONSULTA|tipo|parametro
        String[] partes = mensaje.split("\\|");
        
        if (partes.length < 3) {
            return "ERROR|Formato de consulta invalido";
        }
        
        String tipo = partes[1];
        String parametro = partes[2];
        
        try {
            if (tipo.equals("SALDO")) {
                return consultarSaldo(parametro);
            } else if (tipo.equals("TRANSACCIONES")) {
                return consultarTransacciones(parametro);
            } else if (tipo.equals("PRESTAMOS")) {
                return consultarPrestamos(parametro);
            } else {
                return "ERROR|Tipo de consulta no soportado";
            }
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }
    
    // Valida DNI en RENIEC a traves de RabbitMQ
    private String validarEnRENIEC(String dni) throws Exception {
        // Enviar solicitud a RENIEC
        canal.basicPublish("", QUEUE_VALIDACION_REQUEST, null, 
            dni.getBytes(StandardCharsets.UTF_8));
        
        // Esperar respuesta (implementacion simplificada)
        Thread.sleep(500);
        
        GetResponse response = canal.basicGet(QUEUE_VALIDACION_RESPONSE, true);
        if (response != null) {
            return new String(response.getBody(), StandardCharsets.UTF_8);
        }
        
        return "NO_EXISTE|Timeout esperando respuesta de RENIEC";
    }
    
    // Busca una cuenta por ID
    private String[] buscarCuenta(String idCuenta) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_CUENTAS));
        String linea;
        boolean primeraLinea = true;
        
        while ((linea = br.readLine()) != null) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            
            String[] datos = linea.split("\\|");
            if (datos[0].equals(idCuenta)) {
                br.close();
                return datos;
            }
        }
        
        br.close();
        return null;
    }
    
    // Busca cuenta por ID de cliente
    private String[] buscarCuentaPorCliente(String idCliente) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_CUENTAS));
        String linea;
        boolean primeraLinea = true;
        
        while ((linea = br.readLine()) != null) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            
            String[] datos = linea.split("\\|");
            if (datos[1].equals(idCliente)) {
                br.close();
                return datos;
            }
        }
        
        br.close();
        return null;
    }
    
    // Actualiza el saldo de una cuenta
    private void actualizarSaldoCuenta(String idCuenta, double nuevoSaldo) throws IOException {
        File archivo = new File(RUTA_CUENTAS);
        List<String> lineas = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(archivo));
        
        String linea;
        boolean encontrado = false;
        while ((linea = br.readLine()) != null) {
            String[] datos = linea.split("\\|");
            
            if (datos.length > 0 && datos[0].equals(idCuenta)) {
                // Actualizar esta linea
                String lineaActualizada = String.format("%s|%s|%.2f|%s", 
                    datos[0], datos[1], nuevoSaldo, datos[3]);
                lineas.add(lineaActualizada);
                encontrado = true;
            } else {
                lineas.add(linea);
            }
        }
        br.close();
        
        if (!encontrado) {
            System.err.println("[ERROR] No se encontro la cuenta: " + idCuenta);
            return;
        }
        
        // Escribir archivo actualizado
        BufferedWriter bw = new BufferedWriter(new FileWriter(archivo));
        try {
            for (String l : lineas) {
                bw.write(l);
                bw.newLine();
            }
            bw.flush();
        } finally {
            bw.close();
        }
    }
    
    // Registra una nueva transaccion
    private void registrarTransaccion(String idCuenta, String tipo, double monto, String usuario) throws IOException {
        String idTransaccion = generarIdTransaccion();
        String fecha = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        
        String lineaTransaccion = String.format("%s|%s|%s|%.2f|%s|%s", 
            idTransaccion, idCuenta, tipo, monto, fecha, usuario);
        
        BufferedWriter bw = new BufferedWriter(new FileWriter(RUTA_TRANSACCIONES, true));
        try {
            bw.write(lineaTransaccion);
            bw.newLine();
            bw.flush();
        } finally {
            bw.close();
        }
    }
    
    // Verifica si un cliente tiene prestamos activos
    private boolean tienePrestamosActivos(String idCliente) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_PRESTAMOS));
        String linea;
        boolean primeraLinea = true;
        
        while ((linea = br.readLine()) != null) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            
            String[] datos = linea.split("\\|");
            if (datos[1].equals(idCliente) && datos[4].equals("activo")) {
                br.close();
                return true;
            }
        }
        
        br.close();
        return false;
    }
    
    // Registra un nuevo prestamo
    private void registrarPrestamo(String idPrestamo, String idCliente, double monto) throws IOException {
        String fecha = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        
        BufferedWriter bw = new BufferedWriter(new FileWriter(RUTA_PRESTAMOS, true));
        bw.write(String.format("%s|%s|%.2f|%.2f|activo|%s", 
            idPrestamo, idCliente, monto, monto, fecha));
        bw.newLine();
        bw.close();
    }
    
    // Consulta el saldo de una cuenta
    private String consultarSaldo(String idCuenta) throws IOException {
        String[] datos = buscarCuenta(idCuenta);
        if (datos == null) {
            return "ERROR|Cuenta no encontrada";
        }
        
        return String.format("SALDO|%s|%.2f", idCuenta, Double.parseDouble(datos[2]));
    }
    
    // Consulta transacciones de una cuenta
    private String consultarTransacciones(String idCuenta) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_TRANSACCIONES));
        StringBuilder resultado = new StringBuilder("TRANSACCIONES");
        String linea;
        boolean primeraLinea = true;
        int contador = 0;
        
        while ((linea = br.readLine()) != null) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            
            String[] datos = linea.split("\\|");
            if (datos[1].equals(idCuenta)) {
                resultado.append("|").append(linea);
                contador++;
            }
        }
        
        br.close();
        
        if (contador == 0) {
            return "TRANSACCIONES|Sin transacciones";
        }
        
        return resultado.toString();
    }
    
    // Consulta prestamos de un cliente
    private String consultarPrestamos(String idCliente) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_PRESTAMOS));
        StringBuilder resultado = new StringBuilder("PRESTAMOS");
        String linea;
        boolean primeraLinea = true;
        int contador = 0;
        
        while ((linea = br.readLine()) != null) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            
            String[] datos = linea.split("\\|");
            if (datos[1].equals(idCliente)) {
                resultado.append("|").append(linea);
                contador++;
            }
        }
        
        br.close();
        
        if (contador == 0) {
            return "PRESTAMOS|Sin prestamos";
        }
        
        return resultado.toString();
    }
    
    // Genera un ID unico para transaccion
    private String generarIdTransaccion() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_TRANSACCIONES));
        int maxId = 0;
        String linea;
        
        while ((linea = br.readLine()) != null) {
            if (linea.startsWith("TR")) {
                try {
                    int id = Integer.parseInt(linea.substring(2, 5));
                    if (id > maxId) maxId = id;
                } catch (Exception e) {
                    // Ignorar lineas con formato invalido
                }
            }
        }
        
        br.close();
        return String.format("TR%03d", maxId + 1);
    }
    
    // Genera un ID unico para prestamo
    private String generarIdPrestamo() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(RUTA_PRESTAMOS));
        int maxId = 0;
        String linea;
        
        while ((linea = br.readLine()) != null) {
            if (linea.startsWith("PR")) {
                try {
                    int id = Integer.parseInt(linea.substring(2, 5));
                    if (id > maxId) maxId = id;
                } catch (Exception e) {
                    // Ignorar lineas con formato invalido
                }
            }
        }
        
        br.close();
        return String.format("PR%03d", maxId + 1);
    }
}
