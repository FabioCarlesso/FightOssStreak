import { StatusBar } from 'expo-status-bar';

import { api } from './src/api/client';
import { SmokeScreen } from './src/screens/SmokeScreen';

export default function App() {
  return (
    <>
      <SmokeScreen api={api} />
      <StatusBar style="light" />
    </>
  );
}
