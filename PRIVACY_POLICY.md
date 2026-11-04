# Privacidad de ZKAPriv 3

Actualizada el 3 de noviembre de 2026. Zoka Quantic **testnet v2**.

ZKAPriv es autocustodiada: vos guardás las 24 palabras que permiten recuperar
y gastar tus monedas. La app no manda la frase, las claves de gasto ni el
testigo de una prueba a un servidor. Las pruebas STARK se generan en el
teléfono. Los ZKA de esta red de prueba no tienen valor.

## Almacenamiento local

La frase, el PIN derivado con sal y las preferencias de seguridad quedan en
el almacén cifrado de Android, protegido por Android Keystore. El saldo, las
notas y el historial se guardan en una caché cifrada con ChaCha20-Poly1305,
que puede reconstruirse leyendo la cadena. Los identificadores públicos de
pares aprendidos se guardan aparte; no contienen claves de tu billetera.
Tor mantiene su propia caché. Android verifica la biometría: la app recibe
el resultado, no tu huella.

## Conexiones y datos compartidos

Con Tor apagado, la app pide páginas públicas de bloques y checkpoints a
api.zokascan.cc; puede usar zokascan.cc o pares P2P como alternativa. Pide
bloques por altura y abre sus salidas en el teléfono. No consulta tu saldo
mandando una dirección o una clave de vista a la API.

Publicar tu dirección corta comparte su dirección completa con el
directorio de la red. Al pagar una dirección corta, los pares consultados
reciben esa consulta. Se transmite la transacción ya probada, con salidas
y mensajes cifrados. Los montos y participantes no aparecen en claro en la
cadena. Un mensaje puede ser leído por quien envía y quien recibe, mediante
sus respectivas claves de vista.

Sin Tor, los operadores de la API, su infraestructura y los pares pueden
ver tu IP, horarios y metadatos de conexión. Pueden estar en otros países.
La app no controla sus registros ni promete anonimato absoluto.

## Tor opcional

Podés activar Tor en Ajustes. En ese modo la lectura, publicación de
direcciones y los envíos usan pares onion a través de Arti, integrado en
la app. Si Tor falla, no se usa una conexión directa como reemplazo.
Una operación ya iniciada puede terminar por su ruta original; el cambio
se aplica a las siguientes operaciones. Los enlaces que abras en el
navegador y las apps con las que compartas información usan sus propias
opciones de red y privacidad.

## Permisos y control

Internet y estado de red permiten conectarse. La cámara es opcional para
leer QR, igual que la biometría para autenticarte. Copiar o compartir pone
el contenido a disposición del portapapeles o de la app elegida. La clave
de vista permite leer saldo e historial, pero no gastar. La frase sí permite
gastar: nunca la compartas.

El flujo de ZKAPriv 3 no consulta precios, no incluye publicidad ni envía
telemetría de uso. Las capturas de la billetera están bloqueadas. La vista
de diseño de depuración usa únicamente datos ficticios.

Podés revisar tu frase y clave de vista después de autenticarte, cambiar
el PIN y borrar la billetera desde Ajustes. Desinstalar borra el
almacenamiento privado, pero no transacciones de la cadena, direcciones
publicadas ni copias compartidas. Guardá las 24 palabras antes de borrar:
no tenemos una copia para recuperarlas por vos.

Revisá el código o planteá consultas en el
[repositorio de ZKAPriv](https://github.com/zoka-network/zkapriv).
No publiques frases, claves de vista, PIN ni otros secretos.
