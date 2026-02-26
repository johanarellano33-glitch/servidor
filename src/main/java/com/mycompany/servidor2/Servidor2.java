package com.mycompany.servidor2;

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor2 {

    private static final String ARCHIVO_USUARIOS = "usuarios.txt";
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";
    private static Map<String, Socket> clientesConectados = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, PrintWriter> escritoresClientes = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, List<String>> bandejasDeEntrada = Collections.synchronizedMap(new HashMap<>());
    // Mapa: ClienteOrigen -> ClienteSolicitante (Para reenviar respuestas/errores de transferencia)
    private static Map<String, String> transferenciasPendientes = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        try {
            ServerSocket socketEspecial = new ServerSocket(8080);
            System.out.println("Servidor esperando conexiones en el puerto 8080...");
            
            cargarMensajes();
            
            while (true) {
                Socket cliente = socketEspecial.accept();
                System.out.println("Cliente conectado desde: " + cliente.getInetAddress());
                new Thread(() -> manejarCliente(cliente)).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void manejarCliente(Socket cliente) {
        String usuario = null;
        try {
            PrintWriter escritor = new PrintWriter(cliente.getOutputStream(), true);
            BufferedReader lectorSocket = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            
            String contrasena, opcion;
            
            while ((opcion = lectorSocket.readLine()) != null) {
                System.out.println("DEBUG - Opcion recibida: '" + opcion + "'");
                
                if (opcion.equals("1")) { // Registrarse
                    usuario = lectorSocket.readLine();
                    contrasena = lectorSocket.readLine();
                    if (usuarioExiste(usuario)) {
                        escritor.println("El usuario ya existe.");
                    } else {
                        registrarUsuario(usuario, contrasena);
                        escritor.println("Registro exitoso!");
                    }
                } else if (opcion.equals("2")) { // Iniciar sesión
                    usuario = lectorSocket.readLine();
                    contrasena = lectorSocket.readLine();
                    if (validarCredenciales(usuario, contrasena)) {
                        escritor.println("Bienvenido al servidor, " + usuario + "!");
                        clientesConectados.put(usuario, cliente);
                        escritoresClientes.put(usuario, escritor);
                        manejarSesion(usuario, lectorSocket, escritor);
                        // Sale de manejarSesion, el bucle principal termina y se desconecta.
                        break; 
                    } else {
                        escritor.println("Credenciales incorrectas.");
                    }
                } else if (opcion.equals("3")) { // Dar de baja usuario
                    usuario = lectorSocket.readLine();
                    contrasena = lectorSocket.readLine();
                    String confirmacion = lectorSocket.readLine(); // Leer la confirmación del cliente
                    
                    if (confirmacion.equals("CANCELAR")) { // Cliente canceló antes de enviar credenciales
                         escritor.println("Operacion cancelada.");
                         continue;
                    }
                    
                    if (validarCredenciales(usuario, contrasena)) {
                        if (darDeBajaUsuario(usuario)) {
                            bandejasDeEntrada.remove(usuario);
                            eliminarMensajesDeUsuario(usuario);
                            escritor.println("Usuario dado de baja exitosamente.");
                        } else {
                            escritor.println("Error al dar de baja.");
                        }
                    } else {
                        escritor.println("Credenciales incorrectas.");
                    }
                } else if (opcion.equals("4")) { // Salir (pre-login)
                    escritor.println("Hasta luego!");
                    break;
                } else if (opcion.startsWith("RESPUESTA_ARCHIVOS:")) {
                    manejarRespuestaArchivos(opcion, lectorSocket);
                } else if (opcion.startsWith("RESPUESTA_TRANSFERENCIA:")) {
                    manejarRespuestaTransferencia(opcion, lectorSocket);
                } else if (opcion.startsWith("ERROR:")) {
                    // El usuario actual es quien RECHAZÓ o tuvo el error, y el error debe ir al SOLICITANTE
                    reenviarError(opcion, usuario);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error en conexion: " + (usuario != null ? usuario : cliente.getInetAddress()) + " -> " + e.getMessage());
        } finally {
            if (usuario != null) {
                clientesConectados.remove(usuario);
                escritoresClientes.remove(usuario);
            }
            System.out.println("Cliente desconectado: " + (usuario != null ? usuario : cliente.getInetAddress().toString()));
            try {
                cliente.close();
            } catch (IOException e) { /* Ignorar */ }
        }
    }

    private static void manejarSesion(String usuario, BufferedReader lector, PrintWriter escritor) throws IOException {
        boolean sesionActiva = true;
        while (sesionActiva) {
            String opcionMenu = lector.readLine();
            if (opcionMenu == null) break;
            System.out.println("DEBUG - Menu: '" + opcionMenu + "' de " + usuario);
            
            switch (opcionMenu) {
                case "1":
                    verBandeja(usuario, escritor);
                    break;
                case "2":
                    enviarMensaje(lector, escritor, usuario);
                    break;
                case "3":
                    borrarMensaje(usuario, lector, escritor);
                    break;
                case "4":
                    enviarListaUsuarios(escritor);
                    break;
                case "5":
                    // El cliente maneja el menú de archivos localmente y nos envía solo 6 o 7
                    // No hacemos nada aquí, ya que el cliente manejará el menú
                    break;
                case "6":
                    // SOLICITUD DE LISTAR ARCHIVOS DE OTRO CLIENTE
                    listarArchivosCliente(lector, escritor, usuario); 
                    break;
                case "7":
                    // SOLICITUD DE TRANSFERENCIA DE ARCHIVO
                    solicitarTransferenciaArchivo(lector, escritor, usuario);
                    break;
                case "8": // Opción 6 del cliente (Cerrar sesión) después de la gestión de archivos
                    clientesConectados.remove(usuario);
                    escritoresClientes.remove(usuario);
                    escritor.println("Sesion cerrada. Hasta luego!");
                    sesionActiva = false;
                    break;
                default:
                    escritor.println("Opcion invalida");
                    break;
            }
        }
    }
    
    // === METODOS DE GESTIÓN DE ARCHIVOS DE RED (MODIFICADOS) ===

    private static void listarArchivosCliente(BufferedReader lector, PrintWriter escritor, String solicitante) throws IOException {
        String clienteObjetivo = lector.readLine();
        
        if (!usuarioExiste(clienteObjetivo)) {
            escritor.println("ERROR:USUARIO_NO_EXISTE");
            return;
        }
        
        PrintWriter escritorObjetivo = escritoresClientes.get(clienteObjetivo);
        if (escritorObjetivo == null) {
            escritor.println("ERROR:USUARIO_NO_CONECTADO");
            return;
        }
        
        // 1. Responder OK INMEDIATAMENTE al solicitante para desbloquearlo
        escritor.println("OK:SOLICITUD_ENVIADA"); 
        
        // 2. Reenviar solicitud al cliente objetivo
        escritorObjetivo.println("LISTAR_ARCHIVOS_REQUEST:" + solicitante);
    }

    private static void solicitarTransferenciaArchivo(BufferedReader lector, PrintWriter escritor, String solicitante) throws IOException {
        String clienteOrigen = lector.readLine();
        String archivo = lector.readLine();
        
        if (!usuarioExiste(clienteOrigen)) {
            escritor.println("ERROR:USUARIO_NO_EXISTE");
            return;
        }
        
        PrintWriter escritorOrigen = escritoresClientes.get(clienteOrigen);
        if (escritorOrigen == null) {
            escritor.println("ERROR:CLIENTE_NO_CONECTADO");
            return;
        }
        
        // 1. Responder OK INMEDIATAMENTE al solicitante para desbloquearlo
        escritor.println("OK:SOLICITUD_ENVIADA"); 
        
        // 2. Registrar la transferencia pendiente (para saber a quién reenviar la respuesta)
        transferenciasPendientes.put(clienteOrigen, solicitante);
        
        // 3. Reenviar solicitud al cliente origen
        escritorOrigen.println("SOLICITUD_ARCHIVO:" + archivo + ":" + solicitante);
    }

    private static void manejarRespuestaArchivos(String opcion, BufferedReader lector) throws IOException {
        String[] partes = opcion.split(":");
        if (partes.length < 2) return;
        String destinatario = partes[1];
        PrintWriter escritorDestino = escritoresClientes.get(destinatario);
        
        if (escritorDestino != null) {
            escritorDestino.println("RESPUESTA_ARCHIVOS_INICIO");
            String linea;
            // Lee todas las líneas de la respuesta (incluyendo archivos, NO_ARCHIVOS, SOLICITUD_RECHAZADA)
            while ((linea = lector.readLine()) != null) {
                escritorDestino.println(linea);
                if (linea.equals("FIN_LISTA_ARCHIVOS")) {
                    break;
                }
            }
            escritorDestino.println("RESPUESTA_ARCHIVOS_FIN");
        }
    }

    private static void manejarRespuestaTransferencia(String opcion, BufferedReader lector) throws IOException {
        String[] partes = opcion.split(":");
        if (partes.length < 2) return;
        String remitente = partes[1];
        String destinatario = transferenciasPendientes.remove(remitente); // Obtener el solicitante original
        
        PrintWriter escritorDestino = escritoresClientes.get(destinatario);
        if (escritorDestino != null) {
            escritorDestino.println("INICIO_TRANSFERENCIA");
            String linea;
            // Reenviar todas las líneas de contenido
            while ((linea = lector.readLine()) != null) {
                escritorDestino.println(linea);
                if (linea.equals("FIN_TRANSFERENCIA")) {
                    break;
                } else if (linea.startsWith("ERROR")) {
                    // Si hay un error, salir del bucle
                    break; 
                }
            }
        }
    }

    private static void reenviarError(String error, String remitente) {
        // 'remitente' es el cliente que generó el error (el cliente que tenía el archivo)
        String destinatario = transferenciasPendientes.remove(remitente); // Obtener el solicitante original
        
        if (destinatario != null) {
            PrintWriter escritorDestino = escritoresClientes.get(destinatario);
            if (escritorDestino != null) {
                // Reenviar el error al solicitante original
                escritorDestino.println(error);
            }
        }
    }

    // === MÉTODOS DE MENSAJERÍA Y GESTIÓN DE USUARIOS (IDÉNTICOS AL ORIGINAL) ===
    
    // ... (Métodos verBandeja, enviarMensaje, borrarMensaje, usuarioExiste, 
    // registrarUsuario, validarCredenciales, guardarMensaje, cargarMensajes, 
    // actualizarArchivoDeMensajes, darDeBajaUsuario, eliminarMensajesDeUsuario, 
    // enviarListaUsuarios permanecen sin cambios relevantes al problema)

    private static void verBandeja(String usuario, PrintWriter escritor) {
        List<String> mensajes = bandejasDeEntrada.getOrDefault(usuario, new ArrayList<>());
        if (mensajes.isEmpty()) {
            escritor.println("0 mensajes.");
        } else {
            escritor.println("Tienes " + mensajes.size() + " mensaje(s):");
            for (String mensaje : mensajes) {
                escritor.println(mensaje);
            }
            escritor.println("FIN_MENSAJES");
        }
    }

    private static void enviarMensaje(BufferedReader lector, PrintWriter escritor, String remitente) throws IOException {
        String destinatario = lector.readLine();
        String mensaje = lector.readLine();
        if (usuarioExiste(destinatario)) {
            String mensajeCompleto = "De " + remitente + ": " + mensaje;
            bandejasDeEntrada.computeIfAbsent(destinatario, k -> new ArrayList<>()).add(mensajeCompleto);
            guardarMensaje(remitente, destinatario, mensaje);
            escritor.println("Mensaje enviado exitosamente a " + destinatario);
        } else {
            escritor.println("Error: El destinatario no existe.");
        }
    }

    private static void borrarMensaje(String usuario, BufferedReader lector, PrintWriter escritor) throws IOException {
        List<String> mensajes = bandejasDeEntrada.getOrDefault(usuario, new ArrayList<>());
        if (mensajes.isEmpty()) {
            escritor.println("No tienes mensajes para borrar.");
        } else {
            escritor.println("Tienes " + mensajes.size() + " mensaje(s):");
            for (int i = 0; i < mensajes.size(); i++) {
                escritor.println((i + 1) + ". " + mensajes.get(i));
            }
            escritor.println("FIN_LISTA_BORRAR");
            String numeroStr = lector.readLine();
            try {
                int numero = Integer.parseInt(numeroStr);
                if (numero > 0 && numero <= mensajes.size()) {
                    String borrado = mensajes.remove(numero - 1);
                    actualizarArchivoDeMensajes();
                    escritor.println("Mensaje borrado: " + borrado);
                } else {
                    escritor.println("Error: Numero invalido.");
                }
            } catch (NumberFormatException e) {
                escritor.println("Error: Numero invalido.");
            }
        }
    }
    
    private static boolean usuarioExiste(String usuario) {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length >= 2 && partes[0].trim().equals(usuario)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error al verificar usuario: " + e.getMessage());
        }
        return false;
    }

    private static synchronized void registrarUsuario(String usuario, String contrasena) {
        File archivo = new File(ARCHIVO_USUARIOS);
        try {
            if (!archivo.exists()) archivo.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, true))) {
                writer.write(usuario + ":" + contrasena);
                writer.newLine();
                writer.flush();
            }
            System.out.println("Usuario registrado: " + usuario);
        } catch (IOException e) {
            System.err.println("Error al registrar usuario: " + e.getMessage());
        }
    }

    private static boolean validarCredenciales(String usuario, String contrasena) {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length >= 2 && partes[0].trim().equals(usuario) && partes[1].trim().equals(contrasena)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error al validar credenciales: " + e.getMessage());
        }
        return false;
    }

    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        File archivo = new File(ARCHIVO_MENSAJES);
        try {
            if (!archivo.exists()) archivo.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, true))) {
                writer.write(new Date().toString() + " | " + remitente + " -> " + destinatario + " | " + mensaje);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Error al guardar mensaje: " + e.getMessage());
        }
    }

    private static void cargarMensajes() {
        File archivo = new File(ARCHIVO_MENSAJES);
        if (!archivo.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(" \\| ");
                if (partes.length >= 3) {
                    String[] usuarios = partes[1].split(" -> ");
                    if (usuarios.length >= 2) {
                        String remitente = usuarios[0];
                        String destinatario = usuarios[1];
                        String mensaje = partes[2];
                        String mensajeCompleto = "De " + remitente + ": " + mensaje;
                        bandejasDeEntrada.computeIfAbsent(destinatario, k -> new ArrayList<>()).add(mensajeCompleto);
                    }
                }
            }
            System.out.println("Mensajes cargados desde archivo");
        } catch (IOException e) {
            System.err.println("Error al cargar mensajes: " + e.getMessage());
        }
    }

    private static synchronized void actualizarArchivoDeMensajes() {
        File archivo = new File(ARCHIVO_MENSAJES);
        try {
            File archivoTemp = new File("mensajes_temp.txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivoTemp))) {
                for (Map.Entry<String, List<String>> entrada : bandejasDeEntrada.entrySet()) {
                    String destinatario = entrada.getKey();
                    for (String mensaje : entrada.getValue()) {
                        if (mensaje.startsWith("De ") && mensaje.contains(": ")) {
                            String[] partes = mensaje.split(": ", 2);
                            if (partes.length >= 2) {
                                String remitente = partes[0].substring(3);
                                String contenido = partes[1];
                                writer.write(new Date().toString() + " | " + remitente + " -> " + destinatario + " | " + contenido);
                                writer.newLine();
                            }
                        }
                    }
                }
            }
            if (archivo.exists()) archivo.delete();
            archivoTemp.renameTo(archivo);
        } catch (IOException e) {
            System.err.println("Error al actualizar archivo: " + e.getMessage());
        }
    }

    private static synchronized boolean darDeBajaUsuario(String usuario) {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) return false;
        try {
            List<String> usuariosActivos = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    String[] partes = linea.split(":");
                    if (partes.length >= 2 && !partes[0].trim().equals(usuario)) {
                        usuariosActivos.add(linea);
                    }
                }
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, false))) {
                for (String usuarioActivo : usuariosActivos) {
                    writer.write(usuarioActivo);
                    writer.newLine();
                }
                writer.flush();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error al dar de baja usuario: " + e.getMessage());
            return false;
        }
    }

    private static synchronized void eliminarMensajesDeUsuario(String usuario) {
        File archivo = new File(ARCHIVO_MENSAJES);
        if (!archivo.exists()) return;
        try {
            List<String> mensajesActivos = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    if (linea.contains(" | ")) {
                        String[] partes = linea.split(" \\| ");
                        if (partes.length >= 2) {
                            String[] usuarios = partes[1].split(" -> ");
                            if (usuarios.length >= 2) {
                                String remitente = usuarios[0];
                                String destinatario = usuarios[1];
                                if (!remitente.equals(usuario) && !destinatario.equals(usuario)) {
                                    mensajesActivos.add(linea);
                                }
                            }
                        }
                    }
                }
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, false))) {
                for (String mensajeActivo : mensajesActivos) {
                    writer.write(mensajeActivo);
                    writer.newLine();
                }
                writer.flush();
            }
            for (Map.Entry<String, List<String>> entrada : bandejasDeEntrada.entrySet()) {
                List<String> mensajes = entrada.getValue();
                mensajes.removeIf(mensaje -> mensaje.contains("De " + usuario + ":"));
            }
        } catch (IOException e) {
            System.err.println("Error al eliminar mensajes del usuario: " + e.getMessage());
        }
    }

    private static void enviarListaUsuarios(PrintWriter escritor) {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) {
            escritor.println("No hay usuarios registrados.");
            escritor.println("FIN_LISTA_USUARIOS");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            int contador = 1;
            boolean hayUsuarios = false;
            escritor.println("=== USUARIOS REGISTRADOS ===");
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length >= 1) {
                    escritor.println(contador + ". " + partes[0].trim());
                    contador++;
                    hayUsuarios = true;
                }
            }
            if (!hayUsuarios) {
                escritor.println("No hay usuarios registrados.");
            }
            escritor.println("FIN_LISTA_USUARIOS");
        } catch (IOException e) {
            System.err.println("Error al obtener lista de usuarios: " + e.getMessage());
            escritor.println("Error al obtener la lista de usuarios.");
            escritor.println("FIN_LISTA_USUARIOS");
        }
    }
}