import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View } from 'react-native';
import PrinterTest from './components/PrinterTest';

export default function App() {
  return (
    <View style={styles.container}>
      <StatusBar style="auto" />
      <PrinterTest />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
