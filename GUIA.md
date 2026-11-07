# Candado — instalar usando solo el celular

## A. Convertir el código en una app (APK) — gratis, en la nube
1. En el navegador del celular entra a github.com y crea una cuenta.
2. Toca **+ > New repository**. Nombre: `candado`. Elige **Private**. Crear.
3. En el repositorio: **Add file > Create new file**.
   - En el nombre escribe exactamente: `.github/workflows/build.yml`
   - Pega el contenido del archivo `build.yml` (viene en este zip, carpeta `.github/workflows`). **Commit**.
4. **Add file > Upload files** y sube el archivo **Candado.zip** tal cual (sin descomprimir). **Commit**.
5. Ve a la pestaña **Actions**: verás "Construir APK" trabajando (~6 min). Cuando tenga ✅,
   entra a **Releases** (en la página principal del repo) y descarga **Candado.apk**.

## B. Instalar
1. Abre Candado.apk. Si Android lo pide, permite "Instalar apps desconocidas" para tu navegador.
2. Si Play Protect avisa, toca "Más detalles > Instalar de todas formas" (la app es tuya, no está en la tienda).

## C. Configurar (en este orden)
1. Pestaña **Permisos**: activa los pasos 1 a 4.
2. Pestaña **Apps**: marca las apps a bloquear.
3. Pestaña **Filtro**: enciende el filtro y agrega sitios/palabras.
4. Pestaña **Candados**: pon tus candados (NIP, QR, biométrico, etc.) y, si quieres, bloqueo estricto por meses o años.
5. Opcional: **Modo blindado** (Permisos) para que ni el modo seguro ni restablecer de fábrica lo quiten.

## Actualizar
Sube el nuevo Candado.zip al repo (reemplaza el anterior). El nuevo APK se instala encima sin desinstalar.
