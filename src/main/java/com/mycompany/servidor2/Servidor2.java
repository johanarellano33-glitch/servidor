package com.mycompany.servidor2;

import java.io.*;
import java.net.*;

public class Servidor2 {

    private static final String ARCHIVO_USUARIOS = "usuarios.txt";

    public static void main(String[] args) {
        try {
            ServerSocket socketEspecial = new ServerSocket(8080);
            System.out.println("Servidor esperando conexiones en el puerto 8080...");
            
            while (true) {
                Socket cliente = socketEspecial.accept();
                System.out.println("Cliente conectado desde: " + cliente.getInetAddress());
                
                new Thread(() -> {
                    try {
                        PrintWriter escritor = new PrintWriter(cliente.getOutputStream(), true);
                        BufferedReader lectorSocket = new BufferedReader(
                                new InputStreamReader(cliente.getInputStream())
                        );
                        
                        String usuario, contrasena, opcion;
                        
                        // Bucle principal del servidor donde se preguntan las opciones al cliente
                        while (true) {
                            escritor.println("Bienvenido al servidor.");
                            escritor.println("Seleccione una opción: ");
                            escritor.println("1. Registrarse");
                            escritor.println("2. Iniciar sesión");
                            escritor.println("3. Salir");
                            opcion = lectorSocket.readLine();
                            
                            if (opcion.equals("1")) {
                                // Registro de usuario
                                escritor.println("Usuario: ");
                                usuario = lectorSocket.readLine();
                                escritor.println("Contraseña: ");
                                contrasena = lectorSocket.readLine();
                                 
                                if (usuarioExiste(usuario)) {
                                    escritor.println("El usuario ya existe. Por favor, elige otro.");
                                    escritor.println("Por favor, inicia sesión.");
                                } else {
                                    registrarUsuario(usuario, contrasena);
                                    escritor.println("¡Registro exitoso! Ahora puedes iniciar sesión.");
                                }
                            } else if (opcion.equals("2")) {
                                // Iniciar sesión
                                escritor.println("Usuario: ");
                                usuario = lectorSocket.readLine();
                                escritor.println("Contraseña: ");
                                contrasena = lectorSocket.readLine();
                                
                                if (validarCredenciales(usuario, contrasena)) {
                                    escritor.println("Bienvenido al servidor, " + usuario + "!");
                                    escritor.println("Escribe 'salir' para desconectarte.");
                                    
                                    // Opción para salir del servidor
                                    String comando;
                                    while (!(comando = lectorSocket.readLine()).equalsIgnoreCase("salir")) {
                                        escritor.println("Comando inválido, escribe 'salir' para desconectarte.");
                                    }
                                    escritor.println("Desconectando...");
                                    break; // Salir del bucle principal
                                } else {
                                    escritor.println("Usuario o contraseña incorrectos. Por favor, inténtalo de nuevo.");
                                }
                            } else if (opcion.equals("3")) {
                                escritor.println("Desconectando...");
                                break;
                            } else {
                                escritor.println("Opción inválida. Por favor, elige una opción válida.");
                            }
                        }

                        // Cerrar recursos
                        lectorSocket.close();
                        escritor.close();
                        cliente.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Verificar si el usuario ya existe
    private static boolean usuarioExiste(String usuario) throws IOException {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.split(":")[0].equals(usuario)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Registrar usuario en el archivo de texto
    private static void registrarUsuario(String usuario, String contrasena) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ARCHIVO_USUARIOS, true))) {
            writer.write(usuario + ":" + contrasena);
            writer.newLine();
        }
    }

    // Validar las credenciales de usuario y contraseña
    private static boolean validarCredenciales(String usuario, String contrasena) throws IOException {
        File archivo = new File(ARCHIVO_USUARIOS);
        if (!archivo.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes[0].equals(usuario) && partes[1].equals(contrasena)) {
                    return true;
                }
            }
        }
        return false;
    }
}

