import com.rabbitmq.client.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class ClienteDesktop extends JFrame {
    
    private static final String HOST = "localhost";
    private static final String QUEUE_VALIDACION_REQUEST = "banco.validacion.request";
    private static final String QUEUE_VALIDACION_RESPONSE = "banco.validacion.response";
    private static final String QUEUE_TRANSACCIONES = "banco.transacciones";
    private static final String QUEUE_PRESTAMOS_SOLICITUD = "banco.prestamos.solicitud";
    private static final String QUEUE_PRESTAMOS_RESPUESTA = "banco.prestamos.respuesta";
    private static final String QUEUE_CONSULTAS = "banco.consultas";
    private static final String QUEUE_CONSULTAS_RESPUESTA = "banco.consultas.respuesta";
    
    private Connection conexion;
    private Channel canal;
    
    // Componentes GUI
    private JTabbedPane pestanas;
    private JTable tablaCuentas;
    private DefaultTableModel modeloTabla;
    private JTextField campoDNI, campoIdCliente, campoIdCuenta;
    
    // Datos del usuario actual
    private String idClienteActual = "";
    private String idCuentaActual = "";
    private boolean validado = false;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClienteDesktop cliente = new ClienteDesktop();
            cliente.setVisible(true);
        });
    }
    
    public ClienteDesktop() {
        setTitle("Banco Shibasito - Cliente Desktop");
        setSize(900, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        conectarRabbitMQ();
        inicializarInterfaz();
        iniciarActualizacionTabla();
    }
    
    private void conectarRabbitMQ() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(HOST);
            factory.setUsername("admin");
            factory.setPassword("admin123");
            
            conexion = factory.newConnection();
            canal = conexion.createChannel();
            
            canal.queueDeclare(QUEUE_VALIDACION_REQUEST, true, false, false, null);
            canal.queueDeclare(QUEUE_VALIDACION_RESPONSE, true, false, false, null);
            canal.queueDeclare(QUEUE_TRANSACCIONES, true, false, false, null);
            canal.queueDeclare(QUEUE_PRESTAMOS_SOLICITUD, true, false, false, null);
            canal.queueDeclare(QUEUE_PRESTAMOS_RESPUESTA, true, false, false, null);
            canal.queueDeclare(QUEUE_CONSULTAS, true, false, false, null);
            canal.queueDeclare(QUEUE_CONSULTAS_RESPUESTA, true, false, false, null);
            
            System.out.println("Cliente conectado a RabbitMQ");
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Error conectando a RabbitMQ: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void inicializarInterfaz() {
        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        if (!validado) {
            panelPrincipal.add(crearPanelValidacion(), BorderLayout.CENTER);
        } else {
            inicializarPestanas();
            panelPrincipal.add(pestanas, BorderLayout.CENTER);
        }
        
        // Crear tabla en lugar de log
        String[] columnas = {"ID Cuenta", "Nombre Cliente", "Saldo Actual"};
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        tablaCuentas = new JTable(modeloTabla);
        tablaCuentas.setRowHeight(25);
        tablaCuentas.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        
        JScrollPane scrollTabla = new JScrollPane(tablaCuentas);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Cuentas del Sistema"));
        scrollTabla.setPreferredSize(new Dimension(880, 120));
        panelPrincipal.add(scrollTabla, BorderLayout.SOUTH);
        
        add(panelPrincipal);
    }
    
    private JPanel crearPanelValidacion() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel lblTitulo = new JLabel("Validacion RENIEC");
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(lblTitulo, gbc);
        
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("DNI:"), gbc);
        
        campoDNI = new JTextField(15);
        gbc.gridx = 1;
        panel.add(campoDNI, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("ID Cliente:"), gbc);
        
        campoIdCliente = new JTextField(15);
        gbc.gridx = 1;
        panel.add(campoIdCliente, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("ID Cuenta:"), gbc);
        
        campoIdCuenta = new JTextField(15);
        gbc.gridx = 1;
        panel.add(campoIdCuenta, gbc);
        
        JButton btnValidar = new JButton("Validar y Entrar");
        btnValidar.addActionListener(e -> validarUsuario());
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        panel.add(btnValidar, gbc);
        
        return panel;
    }
    
    private void validarUsuario() {
        String dni = campoDNI.getText().trim();
        idClienteActual = campoIdCliente.getText().trim();
        idCuentaActual = campoIdCuenta.getText().trim();
        
        if (dni.isEmpty() || idClienteActual.isEmpty() || idCuentaActual.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Complete todos los campos");
            return;
        }
        
        try {
            canal.basicPublish("", QUEUE_VALIDACION_REQUEST, null, 
                dni.getBytes(StandardCharsets.UTF_8));
            
            Thread.sleep(1000);
            
            GetResponse response = canal.basicGet(QUEUE_VALIDACION_RESPONSE, true);
            if (response != null) {
                String respuesta = new String(response.getBody(), StandardCharsets.UTF_8);
                
                if (respuesta.startsWith("EXISTE")) {
                    validado = true;
                    getContentPane().removeAll();
                    inicializarInterfaz();
                    revalidate();
                    repaint();
                    
                    log("Validacion exitosa");
                    JOptionPane.showMessageDialog(this, "Bienvenido al sistema");
                } else {
                    JOptionPane.showMessageDialog(this, "DNI no encontrado en RENIEC");
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }
    
    private void inicializarPestanas() {
        pestanas = new JTabbedPane();
        pestanas.addTab("Transacciones", crearPanelTransacciones());
        pestanas.addTab("Prestamos", crearPanelPrestamos());
        pestanas.addTab("Consultas", crearPanelConsultas());
    }
    
    private JPanel crearPanelTransacciones() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel panelForm = new JPanel(new GridLayout(3, 2, 5, 5));
        
        panelForm.add(new JLabel("Tipo:"));
        JComboBox<String> comboTipo = new JComboBox<>(new String[]{"deposito", "retiro"});
        panelForm.add(comboTipo);
        
        panelForm.add(new JLabel("Monto:"));
        JTextField campoMonto = new JTextField();
        panelForm.add(campoMonto);
        
        JButton btnEjecutar = new JButton("Realizar Transaccion");
        btnEjecutar.addActionListener(e -> {
            try {
                String tipo = (String) comboTipo.getSelectedItem();
                double monto = Double.parseDouble(campoMonto.getText());
                
                String mensaje = String.format("TRANSACCION|%s|%s|%.2f|%s",
                    idCuentaActual, tipo, monto, idClienteActual);
                
                canal.basicPublish("", QUEUE_TRANSACCIONES, null, 
                    mensaje.getBytes(StandardCharsets.UTF_8));
                
                log("Transaccion enviada: " + tipo + " - " + monto);
                campoMonto.setText("");
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage());
            }
        });
        
        panelForm.add(btnEjecutar);
        panel.add(panelForm, BorderLayout.NORTH);
        
        return panel;
    }
    
    private JPanel crearPanelPrestamos() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel panelForm = new JPanel(new GridLayout(2, 2, 5, 5));
        
        panelForm.add(new JLabel("Monto:"));
        JTextField campoMonto = new JTextField();
        panelForm.add(campoMonto);
        
        JButton btnSolicitar = new JButton("Solicitar Prestamo");
        btnSolicitar.addActionListener(e -> {
            try {
                double monto = Double.parseDouble(campoMonto.getText());
                
                String mensaje = String.format("PRESTAMO|%s|%s|%.2f",
                    campoDNI.getText(), idClienteActual, monto);
                
                canal.basicPublish("", QUEUE_PRESTAMOS_SOLICITUD, null, 
                    mensaje.getBytes(StandardCharsets.UTF_8));
                
                log("Solicitud de prestamo enviada: " + monto);
                
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        GetResponse response = canal.basicGet(QUEUE_PRESTAMOS_RESPUESTA, true);
                        if (response != null) {
                            String respuesta = new String(response.getBody(), StandardCharsets.UTF_8);
                            SwingUtilities.invokeLater(() -> {
                                log("Respuesta prestamo: " + respuesta);
                                JOptionPane.showMessageDialog(panel, respuesta);
                            });
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
                
                campoMonto.setText("");
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage());
            }
        });
        
        panelForm.add(btnSolicitar);
        panel.add(panelForm, BorderLayout.NORTH);
        
        return panel;
    }
    
    private JPanel crearPanelConsultas() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel panelBotones = new JPanel(new FlowLayout());
        
        JButton btnSaldo = new JButton("Consultar Saldo");
        JButton btnTransacciones = new JButton("Ver Transacciones");
        JButton btnPrestamos = new JButton("Ver Prestamos");
        
        panelBotones.add(btnSaldo);
        panelBotones.add(btnTransacciones);
        panelBotones.add(btnPrestamos);
        
        JTextArea areaResultado = new JTextArea(15, 50);
        areaResultado.setEditable(false);
        
        btnSaldo.addActionListener(e -> consultar("SALDO", idCuentaActual, areaResultado));
        btnTransacciones.addActionListener(e -> consultar("TRANSACCIONES", idCuentaActual, areaResultado));
        btnPrestamos.addActionListener(e -> consultar("PRESTAMOS", idClienteActual, areaResultado));
        
        panel.add(panelBotones, BorderLayout.NORTH);
        panel.add(new JScrollPane(areaResultado), BorderLayout.CENTER);
        
        return panel;
    }
    
    private void consultar(String tipo, String parametro, JTextArea area) {
        try {
            String mensaje = "CONSULTA|" + tipo + "|" + parametro;
            canal.basicPublish("", QUEUE_CONSULTAS, null, 
                mensaje.getBytes(StandardCharsets.UTF_8));
            
            log("Consulta enviada: " + tipo);
            
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    GetResponse response = canal.basicGet(QUEUE_CONSULTAS_RESPUESTA, true);
                    if (response != null) {
                        String respuesta = new String(response.getBody(), StandardCharsets.UTF_8);
                        SwingUtilities.invokeLater(() -> {
                            area.setText("=== " + tipo + " ===\n\n");
                            area.append(respuesta.replace("|", "\n"));
                        });
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
            
        } catch (Exception ex) {
            area.setText("Error: " + ex.getMessage());
        }
    }
    
    private void log(String mensaje) {
        System.out.println("[LOG] " + mensaje);
    }
    
    private void actualizarTabla() {
        new Thread(() -> {
            try {
                File archivoCuentas = new File("databases/bd1-banco/cuentas.txt");
                if (!archivoCuentas.exists()) return;
                
                BufferedReader br = new BufferedReader(new FileReader(archivoCuentas));
                String linea = br.readLine(); // Skip header
                
                java.util.List<Object[]> filas = new ArrayList<>();
                
                while ((linea = br.readLine()) != null) {
                    if (linea.trim().isEmpty()) continue;
                    
                    String[] datos = linea.split("\\|");
                    if (datos.length >= 3) {
                        String idCuenta = datos[0];
                        String idCliente = datos[1];
                        String saldo = String.format("$ %.2f", Double.parseDouble(datos[2]));
                        
                        String nombreCliente = "Cliente " + idCliente;
                        
                        filas.add(new Object[]{idCuenta, nombreCliente, saldo});
                    }
                }
                br.close();
                
                SwingUtilities.invokeLater(() -> {
                    modeloTabla.setRowCount(0);
                    for (Object[] fila : filas) {
                        modeloTabla.addRow(fila);
                    }
                });
                
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }
    
    private void iniciarActualizacionTabla() {
        javax.swing.Timer timer = new javax.swing.Timer(5000, e -> actualizarTabla());
        timer.start();
        actualizarTabla();
    }
}
