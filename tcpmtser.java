import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class tcpmtser {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Numero incorrecto de argumentos. Sintaxis correcta: java tcpmtser num_puerto");
            return;
        }

        int port = Integer.parseInt(args[0]);
        // The executor service to handle multiple clients simultaneously
        ExecutorService executorService = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server iniciado en el puerto " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente ha iniciado conexion desde " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort() + "...");

                // Handle the client connection using a separate thread
                executorService.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Caso 1 -> I/O error: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        // The accumulator must be a 64-bit signed integer
        long accumulator = 0;
        
        // Get the client's address and port
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        int clientPort = clientSocket.getPort();

        try (
            DataInputStream input = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())
        ) {

            while (true) {
                byte[] receivedClientMessageUnfiltered = new byte[4];
                int bytesRead = input.read(receivedClientMessageUnfiltered);

                // If read returns -1, the client has disconnected
                if (bytesRead == -1) {
                    System.out.println("Cliente desde " + clientAddress + ":" + clientPort + " se ha desconectado");
                    break;
                }

                byte[] receivedClientMessage;

                if (receivedClientMessageUnfiltered[0] == 6) {
                    receivedClientMessage = new byte[3];
                    receivedClientMessage[0] = receivedClientMessageUnfiltered[0];
                    receivedClientMessage[1] = receivedClientMessageUnfiltered[1];
                    receivedClientMessage[2] = receivedClientMessageUnfiltered[2];
                } else {
                    receivedClientMessage = new byte[4];
                    receivedClientMessage = receivedClientMessageUnfiltered;
                }

                /*TESTING======================================*/
                //System.out.println("Client message received: " + Arrays.toString(receivedClientMessage));
                //=============================================*/

                // Decode the client message (i.e., evaluate the operation), store the result in the number variable and add it to the accumulator
                MessageContainer decodedMessage = decodeClientMessage(receivedClientMessage, clientAddress, clientPort);
                long number = decodedMessage.getNumber();
                accumulator += number;
                MessageContainer messageToBeEncoded = new MessageContainer(decodedMessage.isErrorMode(), decodedMessage.getErrorText(), accumulator);

                if (decodedMessage.isErrorMode()) {
                    System.out.println("No se ha podido realizar la operacion: " + decodedMessage.getErrorText());
                } else {
                    System.out.println("Resultado: " + number);
                }
                System.out.println("Valor actual del acumulador: " + accumulator);

                byte[] encodedServerMessage = encodeServerMessage(messageToBeEncoded);

                /*TESTING======================================*/
                //System.out.println("Server message sent: " + Arrays.toString(encodedServerMessage));
                //=============================================*/

                // Send the response back to the client immediately after processing the message
                output.write(encodedServerMessage);
                output.flush();
            }
        } catch (IOException e) {
            if (e.getMessage().equals("Connection reset")) {
                // Handle connection reset specifically
                System.out.println("Cliente desde " + clientAddress + ":" + clientPort + " se ha desconectado");
            } 
            else {
                System.err.println("I/O error: " + e.getMessage());
            }
        }
    }

    // Method to evaluate the operation based on the received (and encoded) message from the client
    public static MessageContainer decodeClientMessage(byte[] message, String clientAddress, int clientPort) {

        int op_code = message[0];
        int length = message[1];
        int number1 = message[2];
        int number2 = (length == 2) ? message[3] : 0;

        // Tricky part: to detect overflow, we are going to use BigInteger
        BigInteger bigResult;

        switch (op_code) {
            case 1: // Sum
                System.out.println("Operacion recibida de " + clientAddress + ":" + clientPort + ": " + number1 + "+" + number2);
                bigResult = BigInteger.valueOf(number1).add(BigInteger.valueOf(number2));
                if (bigResult.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || bigResult.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0){
                    return new MessageContainer(true, "Overflow", 0);
                }
                return new MessageContainer(false, "NULL", number1 + number2);
            case 2: // Subtraction
            System.out.println("Operacion recibida de " + clientAddress + ":" + clientPort + ": " + number1 + "-" + number2);
                bigResult = BigInteger.valueOf(number1).subtract(BigInteger.valueOf(number2));
                if (bigResult.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || bigResult.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
                    return new MessageContainer(true, "Overflow", 0);
                }
                return new MessageContainer(false, "NULL", number1 - number2);
            case 3: // Multiplication
                System.out.println("Operacion recibida de :" + clientAddress + ":" + clientPort + ": " + number1 + "*" + number2);
                bigResult = BigInteger.valueOf(number1).multiply(BigInteger.valueOf(number2));
                if (bigResult.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || bigResult.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
                    return new MessageContainer(true, "Overflow", 0);
                }
                return new MessageContainer(false, "NULL", number1 * number2);
            case 4: // Division
                System.out.println("Operacion recibida de " + clientAddress + ":" + clientPort + ": " + number1 + "/" + number2);
                if (number2 == 0) {
                    return new MessageContainer(true, "Can not divide by 0", 0);
                }
                bigResult = BigInteger.valueOf(number1).divide(BigInteger.valueOf(number2));
                if (bigResult.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || bigResult.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
                    return new MessageContainer(true, "Overflow", 0);
                }
                return new MessageContainer(false, "NULL", number1 / number2);
            case 5: // Modulus
                System.out.println("Operacion recibida de " + clientAddress + ":" + clientPort + ": " + number1 + "%" + number2);
                if (number2 == 0) {
                    return new MessageContainer(true, "Can not divide by 0", 0);
                }
                bigResult = BigInteger.valueOf(number1).mod(BigInteger.valueOf(number2));
                if (bigResult.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || bigResult.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
                    return new MessageContainer(true, "Overflow", 0);
                }
                return new MessageContainer(false, "NULL", number1 % number2);
            case 6: // Factorial
                System.out.println("Operacion recibida de " + clientAddress + ":" + clientPort + ": " + number1 + "!");
                if (number1 < 0) {
                    return new MessageContainer(true, "Can not calculate the factorial of a negative number", 0);
                }
                bigResult = factorial(number1);
                if (bigResult.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || bigResult.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
                    return new MessageContainer(true, "Overflow", 0);
                }
                return new MessageContainer(false, "NULL", factorial(number1).longValue());
            default:
                throw new IllegalArgumentException("Invalid operation code");
        }
    }

    // Helper method to compute factorial
    private static BigInteger factorial(int n) {
        if (n == 0) {
            return BigInteger.ONE;
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    // Encoding for the "global" message (i.e., the type=10 message)
    public static byte[] encodeServerMessage(MessageContainer messageContainer) {
        // Extract fields from the message container
        boolean errorMode = messageContainer.isErrorMode();
        String errorText = messageContainer.getErrorText();
        long accumulator = messageContainer.getNumber();
        
        // Calculate the length of the global message
        int globalMessageLength = (errorMode) ? 2 + 2 + errorText.length() + 2 + 8 : 2 + 2 + 8;

        // Create the byte array for the global message
        byte[] globalMessage = new byte[globalMessageLength];

        // Set the type for the global message
        globalMessage[0] = 10;

        // Set the length for the global message
        globalMessage[1] = (byte) (globalMessageLength - 2);

        int currentIndex = 2; // Start from the index 2

        if (errorMode) {
            // Add the error submessage
            byte[] errorSubMessage = encodeErrorSubMessage(errorText);
            System.arraycopy(errorSubMessage, 0, globalMessage, currentIndex, errorSubMessage.length);
            currentIndex += errorSubMessage.length;
        }

        // Add the accumulator submessage
        byte[] accumulatorSubMessage = encodeAccumulatorSubMessage(accumulator);
        System.arraycopy(accumulatorSubMessage, 0, globalMessage, currentIndex, accumulatorSubMessage.length);

        /*TESTING======================================*/
        //System.out.println("Server message sent: " + Arrays.toString(globalMessage));
        //=============================================*/

        return globalMessage;
    }

    // Encoding for the error "submessage" (i.e., the type=11 message)
    private static byte[] encodeErrorSubMessage(String errorText) {
        // Calculate the length of the error submessage
        int errorSubMessageLength = 2 + errorText.length();

        // Create the byte array for the error submessage
        byte[] errorSubMessage = new byte[errorSubMessageLength];

        // Set the type for the error submessage
        errorSubMessage[0] = 11;

        // Set the length for the error submessage
        errorSubMessage[1] = (byte) errorText.length();

        // Convert errorText to bytes
        byte[] errorTextBytes = errorText.getBytes(StandardCharsets.UTF_8);

        // Copy error text bytes to the error submessage array
        System.arraycopy(errorTextBytes, 0, errorSubMessage, 2, errorTextBytes.length);

        /*TESTING======================================*/
        //System.out.println("Encoded error submessage: " + Arrays.toString(errorSubMessage));
        //=============================================*/

        return errorSubMessage;
    }

    // Encoding for the accumulator value "submessage" (i.e., the type=16 message)
    private static byte[] encodeAccumulatorSubMessage(long accumulator) {
        // Create the byte array for the accumulator submessage
        byte[] accumulatorSubMessage = new byte[10];

        // Set the type for the accumulator submessage
        accumulatorSubMessage[0] = 16;

        // Set the length for the accumulator submessage
        accumulatorSubMessage[1] = 8;

        // Convert the accumulator to bytes (64-bit signed integer in big-endian format)
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(accumulator);

        // Copy accumulator bytes to the accumulator submessage array
        System.arraycopy(buffer.array(), 0, accumulatorSubMessage, 2, 8);

        /*TESTING======================================*/
        //System.out.println("Encoded accumulator submessage: " + Arrays.toString(accumulatorSubMessage));
        //=============================================*/

        return accumulatorSubMessage;
    }
}

class MessageContainer {

    private boolean errorMode;
    private String errorText;
    private long number;

    public MessageContainer(boolean errorMode, String errorText, long number) {
        this.errorMode = errorMode;
        this.errorText = errorText;
        this.number = number;
    }

    public boolean isErrorMode() {
        return errorMode;
    }

    public String getErrorText() {
        return errorText;
    }

    public long getNumber() {
        return number;
    }

    public void setErrorMode(boolean errorMode) {
        this.errorMode = errorMode;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    public void setNumber(long number) {
        this.number = number;
    }

}
