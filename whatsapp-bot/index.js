const express = require('express');
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode-terminal');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;

// Inicializar cliente de WhatsApp Web para entornos en la nube (Linux / Render)
const client = new Client({
    authStrategy: new LocalAuth(),
    puppeteer: {
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

// Health Check Endpoint para Render.com
app.get(['/', '/health'], (req, res) => {
    res.status(200).json({
        status: 'ok',
        service: 'Libre2Clock WhatsApp Webhook Bot',
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    });
});

// Generar código QR en la consola la primera vez que se inicia
client.on('qr', (qr) => {
    console.log('\n======================================================');
    console.log('📱 ESCANEA ESTE CÓDIGO QR CON TU WHATSAPP:');
    qrcode.generate(qr, { small: true });
    console.log('======================================================\n');
});

client.on('ready', () => {
    console.log('✅ Bot de WhatsApp listo, conectado y en línea!');
});

// Comando de ayuda: Si escribes "/id" o "/grupo" dentro de cualquier grupo de WhatsApp,
// el bot te responderá automáticamente con el ID exacto de ese grupo.
client.on('message', async (msg) => {
    if (msg.body === '/id' || msg.body === '/grupo') {
        const chat = await msg.getChat();
        if (chat.isGroup) {
            msg.reply(`📍 *ID de este Grupo de WhatsApp:*\n\`${chat.id._serialized}\``);
        } else {
            msg.reply(`📍 Tu Chat ID individual es:\n\`${msg.from}\``);
        }
    }
});

// Endpoint Webhook que recibe las alertas SOS enviadas desde la app Libre2Clock
app.post('/sos-webhook', async (req, res) => {
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
