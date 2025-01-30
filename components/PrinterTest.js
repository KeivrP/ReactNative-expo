import React, { use, useState } from 'react';
import {
  SafeAreaView,
  View,
  Text,
  Button,
  TouchableOpacity,
  StyleSheet,
  NativeModules,
  Alert,
} from 'react-native';

const { NexgoModule } = NativeModules;

const PrinterTest = () => {
  const [nfcData, setNfcData] = useState(null)
  const [error, setError] = useState("")

  const handlePrint = async () => {
    try {
      const receiptData = {
        merchant: "RestaurantXXYxYXY",
        rif: "J-00000000-0",
        address: "Domicilio Fiscal",
        invoiceNumber: "N00018642",
        date: "12/02/2018",
        time: "10:00am",
        items: [
          { description: "Sopa", quantity: 2, price: 120.00 },
          { description: "Pabelon", quantity: 2, price: 200.00 },
          { description: "Juego", quantity: 2, price: 40.00 },
          { description: "Agua Mineral", quantity: 1, price: 20.00 },
          { description: "Quesillo", quantity: 2, price: 60.00 },
          { description: "Cafe", quantity: 1, price: 32.00 }
        ],
        subtotal: 472.00,
        tax: 56.64,    // Ejemplo: 12% de IVA
        service: 70.80, // Ejemplo: 15% de servicio
        total: 599.44
      };
  
      await NexgoModule.printReceipt(receiptData);
      Alert.alert('Éxito', 'Factura impresa correctamente');
    } catch (error) {
      Alert.alert('Error', error.message);
    }
  };

  const readNFC = async () => {
    try {
      setNfcData(null);
      setError(null);
      const data = await NexgoModule.readNFC();
      setNfcData(data);
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Prueba de Impresora Nexgo</Text>
        
        

        <TouchableOpacity 
          style={styles.button}
          onPress={handlePrint}
        >
          <Text style={styles.buttonText}>Imprimir Recibo Formato SeniaT</Text>
        </TouchableOpacity>

        <Button title="Leer NFC" onPress={readNFC} style={{marginTop: 20}}/>
      {nfcData && (
        <View>
          <Text>Número de tarjeta: {nfcData.cardNo}</Text>
          {/* Mostrar otros datos de la tarjeta */}
        </View>
      )}
      {error && <Text style={{ color: 'red' }}>Error: {error}</Text>}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  inputContainer: {
    marginBottom: 20,
  },
  label: {
    fontSize: 16,
    marginBottom: 5,
  },
  input: {
    backgroundColor: 'white',
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 10,
    fontSize: 16,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default PrinterTest;
