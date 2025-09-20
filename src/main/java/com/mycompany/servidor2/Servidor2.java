package com.mycompany.servidor2;

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor2 {

    private static final String ARCHIVO_USUARIOS = "usuarios.txt";
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";
    
    // Mapa sincronizado para evitar problemas de concurrencia
    private static Map<String, List<String>> bandejasDeEntrada = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        try {
            ServerSocket socketEspecial = new ServerSocket(8080);
            System.out.println("Servidor esperando conexiones en el puerto 8080...");
            
            // Cargar mensajes existentes al iniciar el servidor
            cargarMensajes();
            
            while (true) {
                Socket cliente = socketEspecial.accept();
                System.out.println("Cliente conectado desde: " + cliente.getInetAddress());
                
                new Thread(() -> {
                    try {
                        PrintWriter escritor = new PrintWriter(cliente.getOutputStream(), true);
                        BufferedReader lectorSocket = new BufferedReader(
                                new InputStreamReader(cliente.getInputStream())
                        );
                        
                        String usuario = null, contrasena, opcion;
                        
                        // Bucle principal del servidor
                        while ((opcion = lectorSocket.readLine()) != null) {
                            System.out.println("DEBUG - Opción recibida: '" + opcion + "'");
                            
                            if (opcion.equals("1")) {
                                // REGISTRO DE USUARIO
                                usuario = lectorSocket.readLine();
                                contrasena = lectorSocket.readLine();
                                System.out.println("DEBUG - Registro: Usuario=" + usuario + ", Pass=" + contrasena);
                                
                                if (usuario != null && contrasena != null) {
                                    if (usuarioExiste(usuario)) {
                                        escritor.println("El usuario ya existe. Por favor, elige otro.");
                                    } else {
                                        registrarUsuario(usuario, contrasena);
                                        escritor.println("¡Registro exitoso! Ahora puedes iniciar sesión.");
                                    }
                                } else {
                                    escritor.println("Error: Datos incompletos.");
                                }
                                
                            } else if (opcion.equals("2")) {
                                // INICIAR SESIÓN
                                usuario = lectorSocket.readLine();
                                contrasena = lectorSocket.readLine();
                                System.out.println("DEBUG - Login: Usuario=" + usuario + ", Pass=" + contrasena);
                                
                                if (usuario != null && contrasena != null) {
                                    if (validarCredenciales(usuario, contrasena)) {
                                        escritor.println("Bienvenido al servidor, " + usuario + "!");
                                        System.out.println("Usuario " + usuario + " logueado correctamente");
                                        
                                        // MENÚ DE MENSAJES
                                        boolean sesionActiva = true;
                                        while (sesionActiva) {
                                            String opcionMenu = lectorSocket.readLine();
                                            if (opcionMenu == null) break;
                                            
                                            System.out.println("DEBUG - Opción menú: '" + opcionMenu + "'");
                                            
                                            switch (opcionMenu) {
                                                case "1":
                                                    // VER BANDEJA DE ENTRADA
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
                                                    break;
                                                    
                                                case "2":
                                                    // ENVIAR MENSAJE
                                                    String destinatario = lectorSocket.readLine();
                                                    String mensaje = lectorSocket.readLine();
                                                    System.out.println("DEBUG - Mensaje: " + usuario + " -> " + destinatario + ": " + mensaje);
                                                    
                                                    if (destinatario != null && mensaje != null) {
                                                        if (usuarioExiste(destinatario)) {
                                                            String mensajeCompleto = "De " + usuario + ": " + mensaje;
                                                            bandejasDeEntrada.computeIfAbsent(destinatario, k -> new ArrayList<>()).add(mensajeCompleto);
                                                            guardarMensaje(usuario, destinatario, mensaje);
                                                            escritor.println("Mensaje enviado exitosamente a " + destinatario);
                                                        } else {
                                                            escritor.println("Error: El destinatario '" + destinatario + "' no existe.");
                                                        }
                                                    } else {
                                                        escritor.println("Error: Datos incompletos para el mensaje.");
                                                    }
                                                    break;
                                                    case "3":
    // BORRAR MENSAJE
    List<String> mensajesUsuario = bandejasDeEntrada.getOrDefault(usuario, new ArrayList<>());
    if (mensajesUsuario.isEmpty()) {
        escritor.println("No tienes mensajes para borrar.");
    } else {
        escritor.println("Tienes " + mensajesUsuario.size() + " mensaje(s):");
        for (int i = 0; i < mensajesUsuario.size(); i++) {
            escritor.println((i + 1) + ". " + mensajesUsuario.get(i));
        }
        escritor.println("FIN_LISTA_BORRAR");
        
        String numeroStr = lectorSocket.readLine();
        try {
            int numeroMensaje = Integer.parseInt(numeroStr);
            if (numeroMensaje > 0 && numeroMensaje <= mensajesUsuario.size()) {
                String mensajeBorrado = mensajesUsuario.remove(numeroMensaje - 1);
                actualizarArchivoDeMensajes();
                escritor.println("Mensaje borrado exitosamente: " + mensajeBorrado);
            } else {
                escritor.println("Error: Número de mensaje inválido.");
            }
        } catch (NumberFormatException e) {
            escritor.println("Error: Por favor ingresa un número válido.");
        }
    }
    break;
                                                    
                                                case "4":
                                                    // CERRAR SESIÓN
                                                    escritor.println("Sesión cerrada. ¡Hasta luego " + usuario + "!");
                                                    sesionActiva = false;
                                                    System.out.println("Usuario " + usuario + " cerró sesión");
                                                    break;
                                                    
                                                default:
                                                    escritor.println("Opción inválida. Por favor, elige 1, 2 o 3.");
                                                    break;
                                            }
                                        }
                                    } else {
                                        escritor.println("Credenciales incorrectas. Verifica tu usuario y contraseña.");
                                    }
                                } else {
                                    escritor.println("Error: Datos incompletos.");
                                }
                                } else if (opcion.equals("3")) {
    // DAR DE BAJA USUARIO
    usuario = lectorSocket.readLine();
    contrasena = lectorSocket.readLine();
    System.out.println("DEBUG - Baja de usuario: Usuario=" + usuario + ", Pass=" + contrasena);
    
    if (usuario != null && contrasena != null) {
        if (validarCredenciales(usuario, contrasena)) {
            if (darDeBajaUsuario(usuario)) {
                // Eliminar también los mensajes del usuario
                bandejasDeEntrada.remove(usuario);
                eliminarMensajesDeUsuario(usuario);
                escritor.println("Usuario dado de baja exitosamente. ¡Hasta luego!");
                System.out.println("Usuario " + usuario + " dado de baja");
            } else {
                escritor.println("Error al dar de baja el usuario. Inténtalo más tarde.");
            }
        } else {
            escritor.println("Credenciales incorrectas. No se puede dar de baja el usuario.");
        }
    } else {
        escritor.println("Error: Datos incompletos.");
    }
    
                                
                            } else if (opcion.equals("4")) {
                                // SALIR
                                escritor.println("¡Hasta luego! Desconectando del servidor...");
                                break;
                                
                            } else {
    escritor.println("Opción inválida. Por favor, elige 1, 2, 3 o 4.");  // Cambiar de "1, 2 o 3" a "1, 2, 3 o 4"
}
                        }

                        // Cerrar recursos
                        System.out.println("Cliente desconectado: " + cliente.getInetAddress());
                        lectorSocket.close();
                        escritor.close();
                        cliente.close();
                        
                    } catch (IOException e) {
                        System.err.println("Error en conexión con cliente: " + e.getMessage());
                    }
                }).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Verificar si el usuario ya existe
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

    // Registrar usuario
    private static synchronized void registrarUsuario(String usuario, String contrasena) {
        File archivo = new File(ARCHIVO_USUARIOS);
        try {
            if (!archivo.exists()) {
                archivo.createNewFile();
            }
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

    // Validar credenciales
    private static boolean validarCredenciales(String usuario, String contrasena) {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length >= 2 && 
                    partes[0].trim().equals(usuario) && 
                    partes[1].trim().equals(contrasena)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Error al validar credenciales: " + e.getMessage());
        }
        return false;
    }
    
    // Guardar mensaje en archivo
    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        File archivo = new File(ARCHIVO_MENSAJES);
        try {
            if (!archivo.exists()) {
                archivo.createNewFile();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, true))) {
                writer.write(new Date().toString() + " | " + remitente + " -> " + destinatario + " | " + mensaje);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Error al guardar mensaje: " + e.getMessage());
        }
    }
    
    // Cargar mensajes existentes
    private static void cargarMensajes() {
        File archivo = new File(ARCHIVO_MENSAJES);
        if (!archivo.exists()) return;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                // Formato: fecha | remitente -> destinatario | mensaje
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
   // Dar de baja usuario
private static synchronized boolean darDeBajaUsuario(String usuario) {
    File archivo = new File(ARCHIVO_USUARIOS);
    if (!archivo.exists()) return false;
    
    try {
        // Leer todos los usuarios excepto el que se va a dar de baja
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
        
        // Reescribir el archivo sin el usuario dado de baja
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

// Eliminar todos los mensajes relacionados con un usuario
private static synchronized void eliminarMensajesDeUsuario(String usuario) {
    File archivo = new File(ARCHIVO_MENSAJES);
    if (!archivo.exists()) return;
    
    try {
        // Leer mensajes y filtrar los que no involucren al usuario dado de baja
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
        
        // Reescribir el archivo sin los mensajes del usuario dado de baja
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, false))) {
            for (String mensajeActivo : mensajesActivos) {
                writer.write(mensajeActivo);
                writer.newLine();
            }
            writer.flush();
        }
        
        // Limpiar las bandejas en memoria
        for (Map.Entry<String, List<String>> entrada : bandejasDeEntrada.entrySet()) {
            List<String> mensajes = entrada.getValue();
            mensajes.removeIf(mensaje -> mensaje.contains("De " + usuario + ":"));
        }
        
    } catch (IOException e) {
        System.err.println("Error al eliminar mensajes del usuario: " + e.getMessage());
    }
}
}