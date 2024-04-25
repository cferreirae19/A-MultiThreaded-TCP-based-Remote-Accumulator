import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class tcpmtcli {
    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Numero incorrecto de argumentos. Sintaxis correcta: java tcpmtcli direccion_ip_servidor numero_puerto_servidor");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverAddress, port));
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print("Introduce una operacion o QUIT para finalizar (Ejemplos de operaciones validas: 8+5, 8*5, 8!, etc.): ");
                String userInputLine = userInput.readLine();
                if (userInputLine == null || userInputLine.equalsIgnoreCase("QUIT")) {
                    break;
                }

                // We encode the input as a byte array and we send that message to the server
                byte[] encodedClientMessage = encodeClientMessage(userInputLine);
                output.write(encodedClientMessage);
                // Ensure that the data is sent immediately
                output.flush();

                /*TESTING======================================*/
                //System.out.println("Client message sent: " + Arrays.toString(encodedClientMessage));
                //=============================================*/

                long response = decodeServerMessage(socket.getInputStream());
                System.out.println("Respuesta del servidor (valor del acumulador): " + response);
            }

            socket.close();
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

    private static byte[] encodeClientMessage (String s) {
        // Check if the first character is a negative sign
        int number1Mutiplier = 1;
        int number1StartIndex = 0;
        if (s.charAt(0) == '-') {
            number1Mutiplier = -1;
            number1StartIndex = 1;
        }
        
        // Find the index of the operator
        int operatorIndex = -1;
        for (int i = number1StartIndex+1; i < s.length(); i++) {
            if (s.charAt(i) == '+' || s.charAt(i) == '-' || s.charAt(i) == '*' || s.charAt(i) == '/' || s.charAt(i) == '%' || s.charAt(i) == '!' ) {
                operatorIndex = i;
                break;
            }
        }

        // Extract the first number
        int number1 = Integer.parseInt(s.substring(number1StartIndex, operatorIndex)) * number1Mutiplier;

        // Extract the operator
        char operator = s.charAt(operatorIndex);

        //Factorial case (only 1 number)
        if (operator == '!') {
            byte[] message = new byte[3];
            message[0] = 6;
            message[1] = 1;
            message[2] = (byte) number1;
            return message;
        }

        // Check if the second number is negative
        int number2Mutiplier = 1;
        if (s.charAt(operatorIndex + 1) == '-') {
            number2Mutiplier = -1;
            operatorIndex++;
        }

        // Extract the second number
        int number2 = Integer.parseInt(s.substring(operatorIndex + 1)) * number2Mutiplier;

        // Determine op_code and length based on the operator
        byte op_code;
        byte length;

        switch (operator) {
            case '+':
                op_code = 1;
                length = 2;
                break;
            case '-':
                op_code = 2;
                length = 2;
                break;
            case '*':
                op_code = 3;
                length = 2;
                break;
            case '/':
                op_code = 4;
                length = 2;
                break;
            case '%':
                op_code = 5;
                length = 2;
                break;
            default:
                throw new IllegalArgumentException("Operador inválido");
        }

        // Build the message array
        byte[] message = new byte[length + 2];
        message[0] = op_code;
        message[1] = length;
        message[2] = (byte) number1;
        message[3] = (byte) number2;

        return message;
    }

    public static long decodeServerMessage(InputStream inputStream) throws IOException {
        // Read the encoded message (not filtered) from the server
        byte[] encodedMessageUnfiltered = new byte[100];
        inputStream.read(encodedMessageUnfiltered);

        // Filter out the zero-padding from the byte array
        int filteredMessageLength = 2 + encodedMessageUnfiltered[1];
        byte[] encodedMessage = Arrays.copyOf(encodedMessageUnfiltered, filteredMessageLength);

        if (encodedMessage == null || encodedMessage.length < 4) {
            throw new IllegalArgumentException("Mensaje codificado no válido");
        }

        if (encodedMessage[0] != 10) {
            throw new IllegalArgumentException("Formato de mensaje no válido: primer byte debe ser 10");
        }

        int errorStartIndex = -1;
        int accumulatorStartIndex = -1;

        // First case: only 1 TLV (accumulator)
        if (encodedMessage[1] == 10) {
            accumulatorStartIndex = 2;
        }
        // Second case: error TLV followed by accumulator TLV
        else if (encodedMessage[1] > 10 && encodedMessage[2] == 11) {
            errorStartIndex = 2;
            accumulatorStartIndex = encodedMessage[errorStartIndex+1] + 4;
        }
        // Third case: accumulator TLV followed by error TLV
        else if (encodedMessage[1] > 10 && encodedMessage[2] == 16) {
            accumulatorStartIndex = 2;
            errorStartIndex = accumulatorStartIndex + 10;
        } else {
            throw new IllegalArgumentException("Formato de mensaje no válido");
        }

        if (encodedMessage[accumulatorStartIndex] != 16 || encodedMessage[accumulatorStartIndex + 1] != 8) {
            throw new IllegalArgumentException("Formato de mensaje no válido");
        }

        // Accummulator and error message byte arrays
        byte[] accumulatorBytes = Arrays.copyOfRange(encodedMessage, accumulatorStartIndex + 2, accumulatorStartIndex + 10);
        byte[] errorMessageBytes = Arrays.copyOfRange(encodedMessage, errorStartIndex + 2, errorStartIndex + 2 + encodedMessage[errorStartIndex + 1]);

        // Printing error message
        if (errorStartIndex >= 0) {
            String errorMessageString = new String(errorMessageBytes);
            System.out.println("Error: " + errorMessageString);
        }

        return ByteBuffer.wrap(accumulatorBytes).getLong();
    }
}
