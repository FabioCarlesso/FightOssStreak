import { useLocalSearchParams } from 'expo-router';

import { TelaNo } from '../../../src/screens/TelaNo';

export default function RotaDoNo() {
  const { codigo } = useLocalSearchParams<{ codigo: string }>();
  return <TelaNo codigo={codigo ?? ''} />;
}
