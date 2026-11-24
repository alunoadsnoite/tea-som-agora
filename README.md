# TEA Som Agora

Compare o ruído entre dois lugares no mesmo celular, offline.

1. Meça o **ponto A** por 10 segundos.
2. Vá ao **ponto B**, mantenha o celular em posição semelhante e repita.
3. Compare o som captado. Se os níveis forem próximos, o resultado é inconclusivo.

A comparação usa a mediana da intensidade em janelas curtas para reduzir a influência de um pico isolado. Uma diferença de até 3 dB relativos ao sinal digital é inconclusiva. Essa margem é provisória: não representa decibéis ambientais calibrados e precisa de validação em aparelhos. Repetições opcionais ajudam a identificar resultados instáveis; se as leituras variarem, o app informa que não há conclusão clara. O app não mede dB ambientais calibrados nem avalia segurança ou conforto auditivo.

Antes e durante a medição, mantenha o celular parado em posição e orientação semelhantes, não cubra o microfone e evite falar ou trocar a entrada de áudio/acessórios. Os nomes opcionais dos pontos e as leituras existem apenas durante a comparação ativa; nada disso é salvo como histórico.

## Privacidade e configurações

O microfone só é usado durante uma medição solicitada, em primeiro plano. As amostras são processadas em memória e descartadas. As leituras ficam em memória até encerrar a comparação ou fechar o app. Só o tema e o idioma são salvos. Não há conta, anúncios, telemetria ou permissão de internet.

Em Configurações, escolha tema claro, escuro ou automático e idioma português, inglês, espanhol ou francês. O apoio por Pix e GitHub Sponsors é opcional. O app copia a chave Pix ou abre o link externo a pedido da pessoa. O contato com o desenvolvedor abre o app de e-mail.

## Compilar e verificar

Android 8.0 ou superior. Para compilar, use JDK 21 e Android SDK 37. Configure o caminho do SDK em `ANDROID_HOME` ou no Android Studio.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Matriz manual em aparelho (os testes unitários não substituem esta validação):

- Abra o APK em modo avião; confirme que a medição funciona sem rede.
- Teste permissão negada, microfone indisponível/silenciado, sinal saturado, cancelamento e saída da tela.
- Meça pontos semelhantes e claramente diferentes; repita A, B e ambos, incluindo leituras repetidas consistentes e instáveis.
- Repita na ordem inversa, com mais de um aparelho, e com entrada desconhecida, troca de rota/acessório antes e durante a captura.
- Teste TalkBack (início, contagem, conclusão e resultados), texto ampliado, contraste dos temas e os quatro idiomas.
- Renomeie os pontos e encerre/reinicie a comparação; confirme que nomes e leituras não reaparecem como histórico.
- Em Configurações, confira o destino dos links e a cópia da chave Pix.

Código sob GPL-3.0-or-later.
