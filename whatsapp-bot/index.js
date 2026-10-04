const path = require('path');
const express = require('express');
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcodeTerminal = require('qrcode-terminal');
const QRCode = require('qrcode');

// Definir la ruta de caché de Puppeteer dentro de la carpeta del proyecto para Render
const cacheDir = path.join(__dirname, '.cache', 'puppeteer');
process.env.PUPPETEER_CACHE_DIR = cacheDir;

const puppeteer = require('puppeteer');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;

let latestQR = null;

// Inicializar cliente de WhatsApp Web
const client = new Client({
    authStrategy: new LocalAuth(),
    puppeteer: {
        executablePath: puppeteer.executablePath(),
        headless: true,
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--no-first-run',
            '--no-zygote',
            '--single-process',
            '--disable-gpu'
        ]
    }
});

// Guardar y mostrar el código QR
client.on('qr', (qr) => {
    latestQR = qr;
    console.log('\n======================================================');
    console.log('📱 ¡NUEVO CÓDIGO QR GENERADO!');
    console.log('Abre la web en tu navegador para verlo perfecto: /qr');
    console.log('======================================================\n');
    qrcodeTerminal.generate(qr, { small: true });
});

client.on('ready', () => {
    latestQR = null;
    console.log('✅ Bot de WhatsApp listo, conectado y en línea!');
});

// Endpoint API JSON para listar grupos
app.get('/api/groups', async (req, res) => {
    if (!client.info) {
        return res.status(503).json({ error: 'WhatsApp no está conectado aún' });
    }
    try {
        const chats = await client.getChats();
        const groups = chats
            .filter(c => c.isGroup)
            .map(g => ({
                name: g.name,
                id: g.id._serialized,
                unread: g.unreadCount
            }));
        res.json(groups);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Health Check & Página Web con Estado, Código QR y Lista de Grupos
app.get(['/', '/qr', '/groups', '/health'], async (req, res) => {
    // Petición API JSON si solicitan /health
    if (req.path === '/health') {
        return res.status(200).json({
            status: 'ok',
            service: 'Libre2Clock WhatsApp Webhook Bot',
            connected: !latestQR && !!client.info,
            uptime: process.uptime(),
            timestamp: new Date().toISOString()
        });
    }

    // Si ya está conectado y listo -> Mostrar lista de grupos
    if (!latestQR && client.info) {
        let groupsHtml = '<p style="color: #666;">Cargando lista de grupos de WhatsApp...</p>';
        try {
            const chats = await client.getChats();
            const groups = chats.filter(c => c.isGroup);

            if (groups.length === 0) {
                groupsHtml = `
                    <div style="background: #fff3cd; color: #856404; padding: 12px; border-radius: 8px; font-size: 14px; margin-top: 15px;">
                        ⚠️ No se encontraron grupos de WhatsApp en esta cuenta.<br>Crea o únete a un grupo de WhatsApp y recarga esta página.
                    </div>
                `;
            } else {
                groupsHtml = `
                    <div style="text-align: left; margin-top: 20px;">
                        <h3 style="color: #075e54; font-size: 16px; margin-bottom: 10px;">📋 Tus Grupos de WhatsApp:</h3>
                        <div style="display: flex; flex-direction: column; gap: 10px;">
                            ${groups.map(g => `
                                <div style="background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 8px; padding: 12px;">
                                    <div style="font-weight: bold; font-size: 15px; color: #212529;">👥 ${g.name}</div>
                                    <div style="font-family: monospace; font-size: 13px; color: #1e88e5; word-break: break-all; margin-top: 4px;">${g.id._serialized}</div>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
            }
        } catch (e) {
            groupsHtml = `<p style="color: red;">Error al obtener grupos: ${e.message}</p>`;
        }

        return res.send(`
            <!DOCTYPE html>
            <html>
            <head>
                <title>Libre2Clock Bot - Grupos</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; text-align: center; padding: 30px 15px; background: #eef2f5; color: #333; }
                    .card { background: white; max-width: 480px; margin: 0 auto; padding: 25px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); }
                    .status-icon { font-size: 40px; margin-bottom: 5px; }
                    h2 { color: #075e54; margin-bottom: 5px; font-size: 20px; }
                    p { color: #555; line-height: 1.4; font-size: 14px; margin-top: 5px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="status-icon">✅</div>
                    <h2>Bot Conectado y En Línea</h2>
                    <p>Copia el ID del grupo que quieras utilizar y pégalo en la app Libre2Clock en <b>Ajustes ➔ Contactos y Alertas SOS</b>.</p>
                    ${groupsHtml}
                </div>
            </body>
            </html>
        `);
    }

    // Si aún no hay QR generado (cargando)
    if (!latestQR) {
        return res.send(`
            <!DOCTYPE html>
            <html>
            <head>
                <title>Cargando Bot - Libre2Clock</title>
                <meta http-equiv="refresh" content="3">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; text-align: center; padding: 40px 15px; background: #eef2f5; }
                    .card { background: white; max-width: 420px; margin: 0 auto; padding: 30px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>⏳ Generando código QR...</h2>
                    <p>Iniciando el servidor de WhatsApp. Esta página se actualizará automáticamente en unos segundos.</p>
                </div>
            </body>
            </html>
        `);
    }

    // Renderizar la imagen limpia del Código QR
    try {
        const qrImage = await QRCode.toDataURL(latestQR);
        res.send(`
            <!DOCTYPE html>
            <html>
            <head>
                <title>Vincular WhatsApp - Libre2Clock Bot</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; text-align: center; padding: 30px 15px; background: #eef2f5; color: #333; }
                    .card { background: white; max-width: 420px; margin: 0 auto; padding: 25px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); }
                    h2 { color: #075e54; margin-bottom: 8px; }
                    img { width: 100%; max-width: 260px; height: auto; margin: 15px 0; border: 1px solid #eee; border-radius: 8px; padding: 10px; background: #fff; }
                    .instructions { text-align: left; font-size: 14px; color: #444; background: #f9f9f9; padding: 12px 16px; border-radius: 8px; margin-top: 15px; }
                    .instructions ol { padding-left: 20px; margin: 5px 0 0 0; }
                    .instructions li { margin-bottom: 4px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>📱 Vincular WhatsApp</h2>
                    <p style="font-size: 14px; color: #666; margin-top: 0;">Escanea esta imagen con tu teléfono</p>
                    <img src="${qrImage}" alt="Código QR WhatsApp" />
                    <div class="instructions">
                        <b>Pasos en tu móvil:</b>
                        <ol>
                            <li>Abre WhatsApp.</li>
                            <li>Toca en <b>Dispositivos vinculados</b>.</li>
                            <li>Selecciona <b>Vincular un dispositivo</b>.</li>
                        </ol>
                    </div>
                </div>
            </body>
            </html>
        `);
    } catch (err) {
        res.status(500).send("Error al generar la imagen del código QR: " + err.message);
    }
});

// Comando de ayuda en WhatsApp
client.on('message', async (msg) => {
    const text = (msg.body || '').trim().toLowerCase();
    if (text === '/id' || text === '/grupo' || text === '!id' || text === '!grupo') {
        try {
            const chat = await msg.getChat();
            if (chat.isGroup) {
                msg.reply(`📍 *ID de este Grupo de WhatsApp:*\n\`${chat.id._serialized}\``);
            } else {
                msg.reply(`📍 Tu Chat ID individual es:\n\`${msg.from}\``);
            }
        } catch (e) {
            console.error('Error al responder /id:', e);
        }
    }
});

// Endpoint Webhook que recibe las alertas SOS enviadas desde la app Libre2Clock
app.post(['/', '/sos-webhook'], async (req, res) => {
    try {
        const { message, glucose, group_id, contacts } = req.body;
        console.log(`\n⚠️ [SOS] Alerta recibida para glucosa ${glucose} mg/dL`);

        // Determinar a qué grupo/chat enviar la alerta:
        // 1. group_id pasado directamente en el JSON desde Libre2Clock
        // 2. O buscar en la lista de contactos del JSON
        // 3. O variable de entorno GROUP_ID como fallback
        let targetId = group_id || process.env.GROUP_ID;

        if (!targetId && Array.isArray(contacts)) {
            const groupContact = contacts.find(c => c.whatsapp_group_id && c.whatsapp_group_id.trim() !== '');
            if (groupContact) {
                targetId = groupContact.whatsapp_group_id;
            }
        }

        if (!targetId) {
            console.error('❌ Error: No se proporcionó un ID de grupo de WhatsApp (group_id).');
            return res.status(400).json({
                error: 'Falta el ID del grupo de WhatsApp. Configúralo en el contacto dentro de Libre2Clock.'
            });
        }

        console.log(`📤 Enviando mensaje SOS al ID de WhatsApp: ${targetId}`);

        // Enviar el mensaje personalizado de la app directamente
        await client.sendMessage(targetId.trim(), message);

        console.log(`✅ ¡Mensaje SOS entregado con éxito a ${targetId}!`);
        res.json({ success: true, delivered_to: targetId });
    } catch (error) {
        console.error('❌ Error al enviar mensaje SOS por WhatsApp:', error);
        res.status(500).json({ error: error.message });
    }
});

client.initialize();

app.listen(PORT, () => {
    console.log(`🚀 Servidor Webhook de WhatsApp corriendo en puerto ${PORT}`);
});
