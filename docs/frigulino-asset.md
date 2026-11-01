# Frigulino

Origem fornecida pelo usuário: `C:/Users/eriksilva-ieg/Downloads/frigulino.jpeg`.

Arte final do aplicativo: `app/src/main/res/drawable-nodpi/frigulino.png`, PNG com transparência. Foi usado o editor integrado `image_gen`, modo de edição; não houve fallback CLI. O fundo externo foi retirado e o branco do rosto/barriga permanece opaco. O desenho completo foi conservado como referência do mascote.

Os avatares dos balões usam esse recurso sem tint. O fundo do slot usa `@color/ai_background` (`#0E1E40`), igual ao chat. A Home usa o mesmo mascote e a chamada **Converse com o Frigulino**; o título do chat identifica **Frigulino**. A imagem gerada original continua preservada em `.codex/generated_images`; o app referencia somente a cópia no próprio projeto.

Prompt final usado:

> Edit target: the attached Frigulino penguin mascot image. Background removal only. Remove the pale gray/off-white backdrop outside the penguin silhouette and make it genuinely transparent alpha so the Android app's existing chat background shows through exactly. Preserve the original penguin illustration, exact navy blue, white, yellow-orange and peach colors, facial expression, eyes, beak, body, feet, outlines, proportions, all details, framing and composition. Do not redesign, redraw, restyle, add shadows, add text or add a different solid background. Keep the white belly and white face fully opaque; remove only the external backdrop. Deliver a clean transparent PNG cutout with the entire mascot and small natural empty margins for an avatar.
