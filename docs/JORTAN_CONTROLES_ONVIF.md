# Jortan JT-9686: duas lentes, PTZ e luz

## Confirmado nesta instalação

- A caixa identifica Jortan JT-9686. Yoosee é o aplicativo de gerenciamento.
- O vídeo RTSP aberto no Minha Câmera contém as duas vistas empilhadas. O diagnóstico anterior identificou `/onvif1` como a primeira URL reproduzida. Não se comprovou que `/onvif2` seja a segunda lente.
- O app Yoosee apresenta ajustes de luz, imagem e som, detecção, gravação, conexões e reinício. As telas não demonstram quais desses recursos o firmware expõe pela rede local.
- Não foi encontrada documentação pública específica do firmware da JT-9686 que liste comandos de PTZ, luz branca ou configuração acessíveis fora do Yoosee. Um caminho RTSP contendo “onvif” não comprova conformidade ou acesso aos serviços de controle.

## O que terceiros podem usar, quando oferecido pelo dispositivo

| Opção | Serviço candidato | O que comprova suporte |
| --- | --- | --- |
| Girar e inclinar | ONVIF PTZ | Serviço e perfil com configuração PTZ, nós e limites válidos, permissão e movimento curto observado. |
| Infravermelho e visão noturna | ONVIF Imaging e comandos auxiliares | Consultar `GetImagingSettings` e `GetOptions`. O filtro IR-cut não é o refletor branco e seu efeito real depende do firmware. |
| Refletor de luz branca | Saída de relé vinculada ao refletor, comando auxiliar identificado ou API do fabricante | Saída documentada para essa finalidade. Não enviar comandos genéricos a relés cuja função é desconhecida. |
| Imagem, vídeo e parte do áudio | ONVIF Imaging, Media e serviços aplicáveis | Identificar a fonte e ler as opções efetivamente expostas. Somente oferecer os ajustes aceitos pelo firmware. |
| Detecção e histórico | ONVIF Events/Analytics, Recording, Search e Replay | Eventos e gravações realmente expostos. Presença de cartão ou de gravação no Yoosee não comprova integração. |
| Rede, atualização e nuvem | Gestão ONVIF parcial ou serviços do fabricante | Identificar a operação autorizada e sua finalidade. Não copiar o menu Yoosee como se todos os itens fossem ONVIF. |

## Teste local e próximos passos

1. No player, abrir **Mais → Identificar câmera e serviços** para conferir modelo, firmware e PTZ anunciado. Esse relatório não move a câmera e não testa luz.
2. Um cliente como **ONVIF Device Manager** no Windows permite inspecionar serviços, perfis, Imaging e PTZ. No Android, pode-se verificar a descoberta com **tinyCam Monitor** em modo ONVIF, não só RTSP. O **Home Assistant** documenta PTZ e alguns controles de imagem, mas pode não detectar câmeras que só entregam H.265.
3. Antes de adicionar um botão, confirmar qual lente responde ao PTZ, ler as opções de luz e confirmar se o firmware liga o refletor fora do Yoosee. Se não houver um controle correspondente publicado, não presumir equivalência com a função do aplicativo.
4. A versão 1.4.82 corrige apenas a visualização. Em Android, validar as duas lentes, superior/inferior em tela cheia, rotação, PiP, JPEG e gravação com o mesmo fluxo original.

## Fontes

- [Yoosee: PTZ de um NVR pela rede local](https://business.yoosee.com/blog/control-ptz-camera-from-nvr)
- [Yoosee: modos de infravermelho e luz branca](https://business.yoosee.com/blog/cctv-night-vision-not-working)
- [ONVIF: índice de operações](https://www.onvif.org/onvif/ver20/util/operationIndex.html)
- [ONVIF Profile T](https://www.onvif.org/profiles/profile-t/) e [base de conformidade por firmware](https://www.onvif.org/conformant-products/)
- [ONVIF Device Manager](https://sourceforge.net/projects/onvifdm/), [tinyCam Monitor](https://www.tinycammonitor.com/support.html) e [Home Assistant ONVIF](https://www.home-assistant.io/integrations/onvif/)
- [Android: TextureView e recorte de visualizações](https://developer.android.com/reference/android/view/TextureView)
