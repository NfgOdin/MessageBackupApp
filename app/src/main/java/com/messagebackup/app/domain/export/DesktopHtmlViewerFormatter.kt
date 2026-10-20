package com.messagebackup.app.domain.export

import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates an interactive, zero-dependency, self-contained HTML5/JavaScript desktop viewer
 * that allows users to explore, search, filter, and inspect exported conversation archives
 * with full multimedia support (photos, videos, audio) on any modern desktop or mobile browser.
 */
class DesktopHtmlViewerFormatter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Builds the complete standalone index.html page bundling all selected threads and messages.
     *
     * @param threadsWithMessages Map of thread metadata to their list of messages.
     * @param mediaSubdirPrefix Relative folder where media files are located for each thread.
     */
    fun generateDesktopViewer(
        threadsWithMessages: Map<ConversationThread, List<MessageItem>>,
        mediaSubdirPrefix: String = "media/"
    ): String {
        val rootJson = JSONObject()
        val threadsArray = JSONArray()

        for ((thread, messages) in threadsWithMessages) {
            val threadObj = JSONObject().apply {
                put("id", thread.threadId)
                put("title", thread.displayName)
                put("address", thread.address)
                put("msgCount", messages.size)
                put("snippet", thread.snippet)
                put("timestamp", thread.timestamp)
            }

            val messagesArray = JSONArray()
            for (msg in messages) {
                val msgObj = JSONObject().apply {
                    put("id", msg.messageId)
                    put("threadId", msg.threadId)
                    put("direction", if (msg.direction == MessageDirection.OUTGOING) "out" else "in")
                    put("address", msg.sender)
                    put("body", msg.body)
                    put("timestamp", msg.timestamp)
                    val formattedDate = try {
                        dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    } catch (e: Exception) {
                        ""
                    }
                    put("formattedDate", formattedDate)
                    put("isMms", msg.type == com.messagebackup.app.model.MessageType.MMS)

                    val attachmentsArray = JSONArray()
                    for (att in msg.attachments) {
                        val attObj = JSONObject().apply {
                            put("fileName", att.fileName)
                            put("mimeType", att.contentType)
                            // Path relative to index.html in the root archive:
                            val safeThreadDir = sanitizeFilename("${thread.displayName}_${thread.threadId}")
                            put("relPath", "$safeThreadDir/$mediaSubdirPrefix${att.fileName}")
                        }
                        attachmentsArray.put(attObj)
                    }
                    put("attachments", attachmentsArray)
                }
                messagesArray.put(msgObj)
            }
            threadObj.put("messages", messagesArray)
            threadsArray.put(threadObj)
        }

        rootJson.put("threads", threadsArray)
        val rawJsonString = rootJson.toString()

        return buildHtmlDocument(rawJsonString)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50)
            .ifBlank { "thread" }
    }

    private fun buildHtmlDocument(dataJson: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Muninn Archive - Desktop Viewer</title>
    <style>
        :root {
            --bg-base: #0f172a;
            --bg-sidebar: #1e293b;
            --bg-panel: #0f172a;
            --bg-card: #334155;
            --border-color: #334155;
            --primary: #38bdf8;
            --primary-hover: #0284c7;
            --bubble-out: #2563eb;
            --bubble-in: #1e293b;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: var(--font-family);
        }

        body {
            background-color: var(--bg-base);
            color: var(--text-main);
            display: flex;
            height: 100vh;
            overflow: hidden;
        }

        /* SIDEBAR */
        #sidebar {
            width: 380px;
            min-width: 320px;
            max-width: 450px;
            background-color: var(--bg-sidebar);
            border-right: 1px solid var(--border-color);
            display: flex;
            flex-direction: column;
            height: 100%;
        }

        .sidebar-header {
            padding: 20px;
            border-bottom: 1px solid var(--border-color);
            background: linear-gradient(180deg, #1e293b 0%, #172033 100%);
        }

        .brand-title {
            font-size: 1.15rem;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 10px;
            color: var(--primary);
            margin-bottom: 6px;
        }

        .brand-raven-icon {
            width: 30px;
            height: 30px;
            flex-shrink: 0;
            filter: drop-shadow(0 0 6px rgba(56, 189, 248, 0.5));
        }

        .brand-text-m {
            color: #38bdf8;
            font-weight: 900;
            font-size: 1.35rem;
            letter-spacing: -0.5px;
        }

        .brand-subtitle {
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-bottom: 14px;
        }

        .search-box {
            position: relative;
            width: 100%;
        }

        .search-input {
            width: 100%;
            background-color: #0f172a;
            border: 1px solid #475569;
            border-radius: 8px;
            padding: 10px 14px 10px 36px;
            color: var(--text-main);
            font-size: 0.88rem;
            outline: none;
            transition: border-color 0.2s;
        }

        .search-input:focus {
            border-color: var(--primary);
        }

        .search-icon {
            position: absolute;
            left: 12px;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-muted);
            font-size: 0.85rem;
            pointer-events: none;
        }

        .thread-list {
            flex: 1;
            overflow-y: auto;
            list-style: none;
        }

        .thread-item {
            padding: 16px 20px;
            border-bottom: 1px solid rgba(51, 65, 85, 0.4);
            cursor: pointer;
            transition: background 0.15s ease;
            display: flex;
            align-items: flex-start;
            gap: 12px;
        }

        .thread-item:hover {
            background-color: rgba(56, 189, 248, 0.08);
        }

        .thread-item.active {
            background-color: rgba(56, 189, 248, 0.15);
            border-left: 4px solid var(--primary);
        }

        .avatar-circle {
            width: 42px;
            height: 42px;
            border-radius: 50%;
            background: linear-gradient(135deg, #3b82f6, #06b6d4);
            color: #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 1rem;
            flex-shrink: 0;
        }

        .thread-info {
            flex: 1;
            min-width: 0;
        }

        .thread-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 4px;
        }

        .thread-name {
            font-size: 0.95rem;
            font-weight: 600;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            color: var(--text-main);
        }

        .thread-badge {
            font-size: 0.72rem;
            background: #334155;
            color: #cbd5e1;
            padding: 2px 8px;
            border-radius: 12px;
            font-weight: 600;
        }

        .thread-snippet {
            font-size: 0.82rem;
            color: var(--text-muted);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        /* MAIN CHAT PANE */
        #main-pane {
            flex: 1;
            display: flex;
            flex-direction: column;
            background-color: var(--bg-panel);
            height: 100%;
        }

        .chat-header {
            padding: 16px 24px;
            background-color: var(--bg-sidebar);
            border-bottom: 1px solid var(--border-color);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .chat-title-group h2 {
            font-size: 1.15rem;
            font-weight: 600;
            color: #f1f5f9;
        }

        .chat-title-group p {
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-top: 2px;
        }

        .chat-controls {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .filter-input {
            background-color: #0f172a;
            border: 1px solid #475569;
            border-radius: 6px;
            padding: 8px 12px;
            color: #fff;
            font-size: 0.82rem;
            outline: none;
            width: 220px;
        }

        .filter-input:focus {
            border-color: var(--primary);
        }

        .btn-action {
            background-color: #334155;
            border: 1px solid #475569;
            border-radius: 6px;
            padding: 8px 14px;
            color: #cbd5e1;
            font-size: 0.82rem;
            cursor: pointer;
            font-weight: 500;
            display: flex;
            align-items: center;
            gap: 6px;
            transition: all 0.15s ease;
        }

        .btn-action:hover {
            background-color: #475569;
            color: #fff;
        }

        /* MESSAGE STREAM */
        .message-container {
            flex: 1;
            overflow-y: auto;
            padding: 24px 32px;
            display: flex;
            flex-direction: column;
            gap: 14px;
            scroll-behavior: smooth;
        }

        .msg-row {
            display: flex;
            flex-direction: column;
            max-width: 65%;
            width: fit-content;
        }

        .msg-row.out {
            align-self: flex-end;
            align-items: flex-end;
        }

        .msg-row.in {
            align-self: flex-start;
            align-items: flex-start;
        }

        .msg-bubble {
            padding: 12px 18px;
            border-radius: 18px;
            font-size: 0.94rem;
            line-height: 1.45;
            word-break: break-word;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
            position: relative;
        }

        .msg-row.out .msg-bubble {
            background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
            color: #ffffff;
            border-bottom-right-radius: 4px;
        }

        .msg-row.in .msg-bubble {
            background-color: #1e293b;
            color: #f1f5f9;
            border: 1px solid #334155;
            border-bottom-left-radius: 4px;
        }

        .msg-meta {
            font-size: 0.72rem;
            color: var(--text-muted);
            margin-top: 4px;
            padding: 0 4px;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        /* ATTACHMENTS */
        .attachment-card {
            margin-top: 8px;
            border-radius: 12px;
            overflow: hidden;
            background-color: rgba(0, 0, 0, 0.25);
            max-width: 380px;
        }

        .attachment-img {
            width: 100%;
            height: auto;
            max-height: 350px;
            object-fit: cover;
            display: block;
            cursor: pointer;
            transition: opacity 0.2s;
        }

        .attachment-img:hover {
            opacity: 0.9;
        }

        .attachment-audio {
            width: 100%;
            margin-top: 4px;
        }

        .attachment-video {
            width: 100%;
            max-height: 350px;
            display: block;
        }

        .attachment-file-badge {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 10px 14px;
            background: #0f172a;
            color: var(--primary);
            font-size: 0.82rem;
            text-decoration: none;
            border-radius: 8px;
            margin-top: 6px;
        }

        .attachment-file-badge:hover {
            text-decoration: underline;
        }

        /* LIGHTBOX MODAL */
        #lightbox {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.9);
            z-index: 9999;
            align-items: center;
            justify-content: center;
        }

        #lightbox.open {
            display: flex;
        }

        #lightbox-img {
            max-width: 90%;
            max-height: 90%;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
        }

        .lightbox-close {
            position: absolute;
            top: 24px;
            right: 32px;
            color: #fff;
            font-size: 2rem;
            cursor: pointer;
            background: none;
            border: none;
        }

        /* EMPTY STATE */
        .empty-placeholder {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: var(--text-muted);
            text-align: center;
            padding: 30px;
        }

        .empty-placeholder svg {
            width: 64px;
            height: 64px;
            margin-bottom: 16px;
            stroke: #475569;
        }

        mark {
            background-color: #f59e0b;
            color: #000;
            border-radius: 2px;
            padding: 1px 3px;
        }

        /* PRINT & COURT TRANSCRIPT STYLING */
        @media print {
            body {
                background: #ffffff !important;
                color: #000000 !important;
                display: block !important;
                height: auto !important;
                overflow: visible !important;
            }
            #sidebar, .chat-actions, .search-box, #lightbox {
                display: none !important;
            }
            #main-pane {
                width: 100% !important;
                height: auto !important;
                overflow: visible !important;
            }
            .chat-messages {
                overflow: visible !important;
                height: auto !important;
            }
            .message-bubble {
                border: 1px solid #ccc !important;
                color: #000000 !important;
                background: #f8f9fa !important;
                page-break-inside: avoid;
            }
            .message-body {
                color: #000000 !important;
            }
        }

        /* SCROLLBARS */
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        ::-webkit-scrollbar-track {
            background: rgba(15, 23, 42, 0.5);
        }
        ::-webkit-scrollbar-thumb {
            background: #334155;
            border-radius: 4px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: #475569;
        }
    </style>
</head>
<body>

    <!-- SIDEBAR -->
    <div id="sidebar">
        <div class="sidebar-header">
            <div class="brand-title">
                <svg class="brand-raven-icon" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <!-- Stylized Raven-Wing 'M' Sigil -->
                    <path d="M15,85 L35,15 L50,55 L65,15 L85,85 L70,85 L58,40 L50,62 L42,40 L30,85 Z" fill="url(#ravenGradient)" stroke="#38bdf8" stroke-width="2.5" stroke-linejoin="round"/>
                    <circle cx="50" cy="50" r="4.5" fill="#f59e0b" filter="drop-shadow(0 0 3px #f59e0b)"/>
                    <path d="M50,47 L50,53 M48,51 L52,51" stroke="#0f172a" stroke-width="1.5" stroke-linecap="round"/>
                    <defs>
                        <linearGradient id="ravenGradient" x1="15" y1="15" x2="85" y2="85" gradientUnits="userSpaceOnUse">
                            <stop stop-color="#38bdf8"/>
                            <stop offset="0.5" stop-color="#0284c7"/>
                            <stop offset="1" stop-color="#0f172a"/>
                        </linearGradient>
                    </defs>
                </svg>
                <span><span class="brand-text-m">M</span>uninn: Message Memory &amp; Vault</span>
            </div>
            <div class="brand-subtitle" id="archive-stats-text">Standalone Offline Desktop Viewer &bull; 0 KB Cloud</div>
            <div class="search-box">
                <span class="search-icon">&#128269;</span>
                <input type="text" id="thread-search" class="search-input" placeholder="Search contacts & threads..." oninput="filterThreads()">
            </div>
        </div>
        <ul class="thread-list" id="thread-list-container">
            <!-- Dynamically populated -->
        </ul>
    </div>

    <!-- MAIN CHAT PANE -->
    <div id="main-pane">
        <div class="chat-header" id="chat-header" style="display: none;">
            <div class="chat-title-group">
                <h2 id="active-thread-title">Contact Name</h2>
                <p id="active-thread-sub">Phone / Address &bull; 0 messages</p>
            </div>
            <div class="chat-controls">
                <input type="text" id="message-filter" class="filter-input" placeholder="Search in this conversation..." oninput="filterActiveMessages()">
                <button class="btn-action" onclick="scrollToTop()">&#8593; Top</button>
                <button class="btn-action" onclick="scrollToBottom()">&#8595; Latest</button>
            </div>
        </div>

        <div class="message-container" id="message-stream">
            <div class="empty-placeholder">
                <svg viewBox="0 0 24 24" fill="none" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>
                <h3>Select a conversation to start browsing</h3>
                <p style="margin-top: 8px;">Explore text logs, view full-resolution MMS images, and play audio recordings offline.</p>
            </div>
        </div>
    </div>

    <!-- LIGHTBOX -->
    <div id="lightbox" onclick="closeLightbox()">
        <button class="lightbox-close" onclick="closeLightbox()">&times;</button>
        <img id="lightbox-img" src="" alt="Enlarged media preview">
    </div>

    <!-- EMBEDDED DATA -->
    <script>
        const ARCHIVE_DATA = $dataJson;

        let activeThreadId = null;
        let activeQuery = "";

        function initApp() {
            const threads = ARCHIVE_DATA.threads || [];
            let totalMsgs = 0;
            threads.forEach(t => totalMsgs += (t.msgCount || 0));
            document.getElementById('archive-stats-text').textContent = threads.length + ' conversation' + (threads.length > 1 ? 's' : '') + ' • ' + totalMsgs.toLocaleString() + ' messages';

            renderThreadList(threads);

            if (threads.length > 0) {
                selectThread(threads[0].id);
            }
        }

        function renderThreadList(threads) {
            const container = document.getElementById('thread-list-container');
            container.innerHTML = '';

            threads.forEach(t => {
                const li = document.createElement('li');
                li.className = 'thread-item' + (t.id === activeThreadId ? ' active' : '');
                li.id = 'thread-node-' + t.id;
                li.onclick = () => selectThread(t.id);

                const initial = (t.title && t.title.length > 0) ? t.title.charAt(0).toUpperCase() : '#';
                
                li.innerHTML = `
                    <div class="avatar-circle">${'$'}{initial}</div>
                    <div class="thread-info">
                        <div class="thread-row">
                            <span class="thread-name">${'$'}{escapeHtml(t.title)}</span>
                            <span class="thread-badge">${'$'}{(t.msgCount || 0).toLocaleString()}</span>
                        </div>
                        <div class="thread-snippet">${'$'}{escapeHtml(t.snippet || t.address || '')}</div>
                    </div>
                `;
                container.appendChild(li);
            });
        }

        function filterThreads() {
            const query = document.getElementById('thread-search').value.toLowerCase().trim();
            const threads = ARCHIVE_DATA.threads || [];
            const filtered = threads.filter(t => 
                (t.title && t.title.toLowerCase().includes(query)) ||
                (t.address && t.address.toLowerCase().includes(query)) ||
                (t.snippet && t.snippet.toLowerCase().includes(query))
            );
            renderThreadList(filtered);
        }

        function selectThread(threadId) {
            activeThreadId = threadId;
            const threads = ARCHIVE_DATA.threads || [];
            const thread = threads.find(t => t.id === threadId);
            if (!thread) return;

            // Highlight in list
            document.querySelectorAll('.thread-item').forEach(el => el.classList.remove('active'));
            const activeNode = document.getElementById('thread-node-' + threadId);
            if (activeNode) activeNode.classList.add('active');

            // Set Header
            document.getElementById('chat-header').style.display = 'flex';
            document.getElementById('active-thread-title').textContent = thread.title;
            document.getElementById('active-thread-sub').textContent = (thread.address || 'Unknown') + ' • ' + (thread.messages ? thread.messages.length.toLocaleString() : 0) + ' messages';

            document.getElementById('message-filter').value = '';
            activeQuery = '';

            renderMessages(thread.messages || []);
            scrollToBottom();
        }

        function renderMessages(messages) {
            const container = document.getElementById('message-stream');
            container.innerHTML = '';

            if (messages.length === 0) {
                container.innerHTML = '<div class="empty-placeholder"><h3>No messages in this conversation.</h3></div>';
                return;
            }

            messages.forEach(msg => {
                const isOut = msg.direction === 'out';
                const row = document.createElement('div');
                row.className = 'msg-row ' + (isOut ? 'out' : 'in');

                let bodyHtml = escapeHtml(msg.body || '');
                if (activeQuery) {
                    const regex = new RegExp('(' + escapeRegex(activeQuery) + ')', 'gi');
                    bodyHtml = bodyHtml.replace(regex, '<mark>$1</mark>');
                }

                let attachmentsHtml = '';
                if (msg.attachments && msg.attachments.length > 0) {
                    msg.attachments.forEach(att => {
                        const mime = att.mimeType || '';
                        if (mime.startsWith('image/')) {
                            attachmentsHtml += `
                                <div class="attachment-card">
                                    <img class="attachment-img" src="${'$'}{att.relPath}" alt="${'$'}{escapeHtml(att.fileName)}" onclick="openLightbox('${'$'}{att.relPath}')" onerror="this.onerror=null; this.parentElement.innerHTML='<span style=\'padding:10px;font-size:0.75rem;color:#94a3b8;\'>⚠️ Attachment file missing or moved</span>';" />
                                </div>
                            `;
                        } else if (mime.startsWith('audio/')) {
                            attachmentsHtml += `
                                <div class="attachment-card" style="padding: 8px;">
                                    <audio class="attachment-audio" controls src="${'$'}{att.relPath}"></audio>
                                </div>
                            `;
                        } else if (mime.startsWith('video/')) {
                            attachmentsHtml += `
                                <div class="attachment-card">
                                    <video class="attachment-video" controls src="${'$'}{att.relPath}"></video>
                                </div>
                            `;
                        } else {
                            attachmentsHtml += `
                                <a class="attachment-file-badge" href="${'$'}{att.relPath}" target="_blank">
                                    📎 ${'$'}{escapeHtml(att.fileName)} (${'$'}{formatFileSize(att.fileSize)})
                                </a>
                            `;
                        }
                    });
                }

                row.innerHTML = `
                    <div class="msg-bubble">
                        ${'$'}{bodyHtml ? '<div>' + bodyHtml + '</div>' : ''}
                        ${'$'}{attachmentsHtml}
                    </div>
                    <div class="msg-meta">
                        <span>${'$'}{isOut ? 'You' : escapeHtml(msg.address || 'Them')}</span>
                        <span>&bull;</span>
                        <span>${'$'}{msg.formattedDate || ''}</span>
                        ${'$'}{msg.isMms ? '<span>&bull; MMS</span>' : ''}
                    </div>
                `;

                container.appendChild(row);
            });
        }

        function filterActiveMessages() {
            activeQuery = document.getElementById('message-filter').value.trim();
            const thread = (ARCHIVE_DATA.threads || []).find(t => t.id === activeThreadId);
            if (!thread) return;

            if (!activeQuery) {
                renderMessages(thread.messages || []);
                return;
            }

            const lower = activeQuery.toLowerCase();
            const filtered = (thread.messages || []).filter(m => 
                (m.body && m.body.toLowerCase().includes(lower)) ||
                (m.attachments && m.attachments.some(a => a.fileName && a.fileName.toLowerCase().includes(lower)))
            );
            renderMessages(filtered);
        }

        function scrollToTop() {
            const container = document.getElementById('message-stream');
            container.scrollTop = 0;
        }

        function scrollToBottom() {
            const container = document.getElementById('message-stream');
            container.scrollTop = container.scrollHeight;
        }

        function openLightbox(src) {
            const lb = document.getElementById('lightbox');
            const img = document.getElementById('lightbox-img');
            img.src = src;
            lb.classList.add('open');
        }

        function closeLightbox() {
            document.getElementById('lightbox').classList.remove('open');
        }

        function formatFileSize(bytes) {
            if (!bytes || bytes <= 0) return '0 B';
            const units = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(1024));
            return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i];
        }

        function escapeHtml(text) {
            if (!text) return '';
            return text.replace(/&/g, '&amp;')
                       .replace(/</g, '&lt;')
                       .replace(/>/g, '&gt;')
                       .replace(/"/g, '&quot;')
                       .replace(/'/g, '&#039;');
        }

        function escapeRegex(text) {
            return text.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\${'$'}&');
        }

        window.addEventListener('DOMContentLoaded', initApp);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
