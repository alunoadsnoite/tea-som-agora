# TEA Som Agora

Compare o ruído entre dois lugares no mesmo celular, offline.

1. Meça o **ponto A** por 10 segundos.
2. Vá ao **ponto B**, mantenha o celular em posição semelhante e repita.
3. Compare o som captado. Se os níveis forem próximos, o resultado é inconclusivo.

A comparação usa a mediana da intensidade em janelas curtas para reduzir a influência de um pico isolado. A margem inicial é de 3 dB relativos ao sinal digital e ainda precisa de validação em aparelhos. O app não mostra decibéis ambientais calibrados nem avalia segurança ou conforto auditivo.

## Privacidade e configurações

O microfone só é usado durante uma medição solicitada, em primeiro plano. As amostras são processadas em memória e descartadas. As leituras ficam em memória até encerrar a comparação ou fechar o app. Só o tema e o idioma são salvos. Não há conta, anúncios, telemetria ou permissão de internet.

Em Configurações, escolha tema claro, escuro ou automático e idioma português, inglês, espanhol ou francês. O apoio por Pix e GitHub Sponsors é opcional. O app copia a chave Pix ou abre o link externo a pedido da pessoa. O contato com o desenvolvedor abre o app de e-mail.

## Compilar e verificar

Android 8.0 ou superior. Para compilar, use JDK 21 e Android SDK 37. Configure o caminho do SDK em `ANDROID_HOME` ou no Android Studio.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Em aparelho, abra o APK em modo avião. Teste permissão negada, medição cancelada, saída da tela, dois pontos semelhantes e dois pontos claramente diferentes. Repita na ordem inversa e com mais de um aparelho. Teste TalkBack, texto ampliado e a troca dos quatro idiomas. Em Configurações, confira o destino dos links e a cópia da chave Pix.

Código sob GPL-3.0-or-later.
