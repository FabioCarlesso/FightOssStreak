import type { VideoView } from '@fos/types';
import { Linking, Text, useWindowDimensions, View } from 'react-native';
import YoutubePlayer from 'react-native-youtube-iframe';

import { estilos } from '../theme';

/**
 * Vídeo pelo player oficial do YouTube, embutido (D7): nada é baixado nem re-hospedado, e o crédito
 * ao canal fica junto do player. Nó sem vídeo catalogado é estado normal, e a tela diz isso.
 */
export function Video({ video }: { video?: VideoView }) {
  const { width } = useWindowDimensions();

  if (!video?.catalogued || !video.youtubeId) {
    return (
      <Text style={estilos.dica}>
        Vídeo ainda não catalogado para este nó. A escolha de um bom vídeo por nó é etapa de
        curadoria manual.
      </Text>
    );
  }

  // Largura útil do cartão (a tela menos as margens) em 16:9.
  const largura = width - 64;
  return (
    <View style={{ gap: 6 }}>
      <YoutubePlayer
        height={Math.round((largura * 9) / 16)}
        width={largura}
        videoId={video.youtubeId}
        initialPlayerParams={{ start: video.startSeconds ?? undefined }}
      />
      <Text style={estilos.dica} testID="credito-video">
        {video.title ? `${video.title} — ` : ''}canal {video.channel ?? 'desconhecido'}
      </Text>
      {video.watchUrl ? (
        <Text
          style={[estilos.dica, { textDecorationLine: 'underline' }]}
          accessibilityRole="link"
          onPress={() => void Linking.openURL(video.watchUrl!)}
        >
          Assistir no YouTube
        </Text>
      ) : null}
    </View>
  );
}
