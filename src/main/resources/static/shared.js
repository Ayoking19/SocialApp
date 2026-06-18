/* ========================================= */
/* --- SHARED SOCIAL CORE (V1.9 - X-STYLE ARCHITECTURE) --- */
/* ========================================= */

const API_BASE = "https://socialappwebsite.me";

const currentUser = localStorage.getItem("currentUser");

/* --- THE UI/UX SKELETON ENGINE --- */
document.head.insertAdjacentHTML("beforeend", `
    <style>
        .skeleton-post { border: 1px solid #334155; padding: 15px; margin-bottom: 20px; background: rgba(255,255,255,0.02); border-radius: 15px; }
        .skeleton-element { background: rgba(255, 255, 255, 0.05); border-radius: 8px; position: relative; overflow: hidden; }
        .skeleton-element::after {
            content: ""; position: absolute; top: 0; left: 0; width: 100%; height: 100%;
            background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
            animation: shimmer 1.2s infinite;
        }
        @keyframes shimmer { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }
        @keyframes spin { 100% { transform: rotate(360deg); } }
        .skel-avatar { width: 40px; height: 40px; border-radius: 50%; }
        .skel-line { height: 14px; margin-bottom: 10px; width: 100%; }
        .skel-media { height: 250px; border-radius: 15px; margin-top: 15px; }

        /* MULTI-MEDIA 2x2 GRID CSS */
        /* THE FIX: Enforce flex-shrink and box-sizing to protect grids from keyboard crushing and horizontal bleeding! */
        .media-gallery { display: grid; gap: 5px; margin-top: 15px; border-radius: 15px; overflow: visible; width: 100%; max-width: 100%; box-sizing: border-box; border: 1px solid rgba(0, 230, 118, 0.3); padding: 5px; flex-shrink: 0; }
        .media-grid-1 { grid-template-columns: 1fr; grid-auto-rows: minmax(200px, 300px); }
        .media-grid-2 { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(150px, 200px); }
        .media-grid-3 { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(100px, 150px); }
        .media-grid-3 .media-cell:first-child { grid-column: span 2; grid-row: span 1; }
        .media-grid-4 { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(100px, 150px); }
        .media-cell { position: relative; width: 100%; height: 100%; max-width: 100%; overflow: visible; display: flex; box-sizing: border-box; }
        /* THE FIX: Move the border-radius directly to the image/video so they stay beautifully rounded without a clipping mask! */
        .grid-media-item { position: absolute; top: 0; left: 0; width: 100%; height: 100%; max-width: 100%; object-fit: cover; background: #0b0f1a; cursor: pointer; border: none; border-radius: 10px; box-sizing: border-box; }

        /* THE VOICE NOTE CSS */
        @keyframes pulseMic { 0% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.2); opacity: 0.7; } 100% { transform: scale(1); opacity: 1; } }
        .recording-pulse { animation: pulseMic 1.2s infinite; }

        /* THE KEBAB MENU CSS */
        .kebab-menu-container { position: relative; display: inline-block; }
        .kebab-btn { background: transparent; border: none; color: #a09eb5; cursor: pointer; padding: 4px; display: flex; align-items: center; justify-content: center; border-radius: 50%; transition: 0.2s; outline: none; }
        .kebab-btn:hover { background: rgba(255,255,255,0.1); color: white; }
        .dropdown-menu { display: none; position: absolute; right: 0; top: 100%; background: #1e293b; border: 1px solid #334155; border-radius: 8px; box-shadow: 0 5px 15px rgba(0,0,0,0.5); z-index: 1000; min-width: 150px; overflow: hidden; flex-direction: column; }
        .dropdown-item { padding: 12px 15px; color: white; cursor: pointer; display: flex; align-items: center; gap: 10px; font-size: 14px; border: none; background: transparent; width: 100%; text-align: left; transition: 0.2s; font-family: inherit; }
        .dropdown-item:hover { background: rgba(255,255,255,0.05); }
        .dropdown-item.danger { color: #ff3366; }
        .dropdown-item.danger:hover { background: rgba(255, 51, 102, 0.1); }

        @keyframes deepLinkFlash {
            0% { background-color: rgba(0, 230, 118, 0.3); box-shadow: 0 0 15px rgba(0,230,118,0.4); transform: scale(1.02); }
            10% { background-color: rgba(0, 230, 118, 0.4); transform: scale(1.02); }
            100% { background-color: rgba(255, 255, 255, 0.03); box-shadow: none; transform: scale(1); }
        }
        .deep-link-glow {
            animation: deepLinkFlash 2.5s ease-out forwards !important;
            border-left: 4px solid #00e676 !important;
        }
    </style>
`);

function generateSkeletonHTML(count = 3) {
    let html = "";
    for (let i = 0; i < count; i++) {
        html += `
        <div class="skeleton-post">
            <div style="display: flex; align-items: center; gap: 15px; margin-bottom: 15px;">
                <div class="skeleton-element skel-avatar"></div>
                <div style="flex: 1;">
                    <div class="skeleton-element skel-line" style="width: 30%;"></div>
                    <div class="skeleton-element skel-line" style="width: 15%; height: 10px;"></div>
                </div>
            </div>
            <div class="skeleton-element skel-line"></div>
            <div class="skeleton-element skel-line"></div>
            <div class="skeleton-element skel-line" style="width: 60%;"></div>
            <div class="skeleton-element skel-media"></div>
        </div>`;
    }
    return html;
}

function checkSecurity() {
    if (!currentUser) {
        window.location.href = "login.html";
        return;
    }
    // THE FIX: Secure Auth Validation to prevent stale localStorage ghost logins!
    fetch(`${API_BASE}/api/validateSession`, {
        method: 'POST',
        body: JSON.stringify({ username: currentUser })
    })
    .then(res => res.text())
    .then(data => {
        if (data === "INVALID" || data === "NOT_FOUND") {
            localStorage.removeItem("currentUser");
            window.location.href = "login.html";
        }
    })
    .catch(err => console.warn("Session check offline or failed"));
}

/* --- THE NEW DESIGN-STANDARD MODAL ENGINE --- */

function openConfirmModal(message, onConfirm) {
    let modal = document.getElementById('customConfirmModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'customConfirmModal';
        modal.className = 'modal-overlay';
        modal.style.zIndex = '5500'; 
        modal.innerHTML = `
            <div class="modal-card">
                <div class="modal-icon"><span class="material-icons" style="color:#ff3366; font-size:40px;">warning</span></div>
                <h3>Are you sure?</h3>
                <p id="confirmMessage" style="color: #a09eb5; margin: 20px 0;"></p>
                <div class="modal-buttons">
                    <button class="modal-cancel-btn" onclick="document.getElementById('customConfirmModal').style.display='none'">Cancel</button>
                    <button class="modal-confirm-btn" id="confirmBtn">Confirm</button>
                </div>
            </div>`;
        document.body.appendChild(modal);
    }
    document.getElementById('confirmMessage').innerText = message;
    modal.style.display = 'flex';
    document.getElementById('confirmBtn').onclick = () => {
        onConfirm();
        modal.style.display = 'none';
    };
}

function openEditModal(title, currentContent, onSave) {
    let modal = document.getElementById('customEditModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'customEditModal';
        modal.className = 'modal-overlay';
        modal.style.zIndex = '5500';
        modal.innerHTML = `
            <div class="modal-card">
                <h3 id="editModalTitle"></h3>
                <textarea id="editModalInput" class="bio-edit-input" style="height:150px; margin: 20px 0; width: 100%; border: 1px solid #00e676;"></textarea>
                <div class="modal-buttons">
                    <button class="modal-cancel-btn" onclick="document.getElementById('customEditModal').style.display='none'">Cancel</button>
                    <button class="modal-confirm-btn" id="saveBtn" style="background:#00e676; color:black; border:none; box-shadow: 0 4px 15px rgba(0, 230, 118, 0.3);">Save Changes</button>
                </div>
            </div>`;
        document.body.appendChild(modal);
    }
    document.getElementById('editModalTitle').innerText = title;
    document.getElementById('editModalInput').value = currentContent;
    modal.style.display = 'flex';
    document.getElementById('saveBtn').onclick = () => {
        onSave(document.getElementById('editModalInput').value);
        modal.style.display = 'none';
    };
}

function openLogoutModal() {
    const menu = document.getElementById('fullMenu');
    if (menu) menu.classList.remove('open');
    const modal = document.getElementById('logoutModal');
    if (modal) modal.style.display = 'flex';
}

function closeLogoutModal() {
    const modal = document.getElementById('logoutModal');
    if (modal) modal.style.display = 'none';
}

function executeLogout() {
    localStorage.removeItem("currentUser"); 
    window.location.href = "login.html"; 
}

window.forceDownload = function(url, filename) {
    fetch(url)
        .then(response => response.blob())
        .then(blob => {
            const blobUrl = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = blobUrl;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(blobUrl);
            document.body.removeChild(a);
        })
        .catch(() => window.open(url));
};

function showToast(message) {
    let toast = document.getElementById('custom-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'custom-toast';
        toast.style.position = 'fixed';
        toast.style.bottom = '30px';
        toast.style.left = '50%';
        toast.style.transform = 'translateX(-50%)';
        toast.style.backgroundColor = '#00e676'; 
        toast.style.color = '#0f172a'; 
        toast.style.padding = '12px 24px';
        toast.style.borderRadius = '25px';
        toast.style.zIndex = '6000';
        toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
        toast.style.fontWeight = 'bold';
        toast.style.transition = 'opacity 0.3s ease-in-out';
        toast.style.pointerEvents = 'none';
        document.body.appendChild(toast);
    }
    toast.innerText = message;
    toast.style.opacity = '1';
    toast.style.display = 'block';

    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.style.display = 'none', 300);
    }, 3000);
}

/* ========================================= */
/* --- POST CRUD OPERATIONS ---              */
/* ========================================= */

function editPost(postId, currentContent) {
    openEditModal("Edit Post", currentContent, (newContent) => {
        if (newContent && newContent.trim() !== "") {
            // OPTIMISTIC UI: INSTANT UPDATE
            document.querySelectorAll(`[id="post-content-text-${postId}"]`).forEach(el => {
                el.innerHTML = parseSocialText(newContent);
            });

            fetch(`${API_BASE}/api/editPost`, {
                method: 'POST',
                body: JSON.stringify({ username: currentUser, postId: postId.toString(), content: newContent })
            })
            .then(res => res.text())
            .then(data => {
                if (data === "SUCCESS") {
                    showToast("Post updated!");
                    document.querySelectorAll(`[id="edit-btn-${postId}"]`).forEach(editBtn => {
                        const escapedContent = newContent.replace(/'/g, "\\'");
                        editBtn.setAttribute("onclick", `event.stopPropagation(); editPost(${postId}, '${escapedContent}')`);
                    });
                }
            });
        }
    });
}

function deletePost(postId) {
    openConfirmModal("Are you sure you want to permanently delete this post?", () => {
        // OPTIMISTIC UI: INSTANT HIDE
        document.querySelectorAll(`[id="post-card-${postId}"]`).forEach(el => el.style.display = 'none');

        fetch(`${API_BASE}/api/deletePost`, {
            method: 'POST',
            body: JSON.stringify({ username: currentUser, postId: postId.toString() })
        })
        .then(res => res.text())
        .then(data => {
            if (data === "SUCCESS") showToast("Post deleted.");
        });
    });
}

/* ========================================= */
/* --- INTERACTIVE COMMENT ENGINE ---        */
/* ========================================= */

function toggleLike(element, postId) {
    fetch(`${API_BASE}/api/toggleLike`, {
        method: 'POST',
        body: JSON.stringify({ username: currentUser, postId: postId.toString() })
    })
    .then(response => response.text())
    .then(data => {
        const likeCountSpan = element.querySelector('.like-count');
        let currentLikes = parseInt(likeCountSpan.textContent);

        if (data === "LIKED") {
            likeCountSpan.textContent = currentLikes + 1;
            element.style.color = "#ff3366"; 
            element.style.background = "rgba(255, 51, 102, 0.1)"; 
        } else if (data === "UNLIKED") {
            likeCountSpan.textContent = currentLikes - 1;
            element.style.color = ""; 
            element.style.background = ""; 
        }
    })
    .catch(error => console.error("Like Error:", error));
}

function toggleCommentLike(element, commentId) {
    fetch(`${API_BASE}/api/toggleCommentLike`, {
        method: 'POST',
        body: JSON.stringify({ username: currentUser, commentId: commentId.toString() })
    })
    .then(response => response.text())
    .then(data => {
        const countSpan = element.querySelector('.like-count') || element.querySelector('span:last-child');
        let currentLikes = parseInt(countSpan.textContent) || 0;
        
        if (data === "LIKED") {
            countSpan.textContent = currentLikes + 1;
            element.style.color = "#ff3366";
            element.style.background = "rgba(255, 51, 102, 0.1)";
        } else if (data === "UNLIKED") {
            countSpan.textContent = currentLikes - 1;
            element.style.color = "";
            element.style.background = "";
        }
    });
}

function editComment(commentId, currentContent) {
    openEditModal("Edit Comment", currentContent, (newContent) => {
        if (newContent && newContent.trim() !== "") {
            fetch(`${API_BASE}/api/editComment`, {
                method: 'POST',
                body: JSON.stringify({ username: currentUser, commentId: commentId.toString(), content: newContent })
            })
            .then(res => res.text())
            .then(data => {
                if (data === "SUCCESS") {
                    showToast("Comment updated!");
                    if (typeof loadComments === 'function') {
                        const urlParams = new URLSearchParams(window.location.search);
                        const postId = urlParams.get('id');
                        if (postId) loadComments(postId);
                    }
                }
            });
        }
    });
}

function deleteComment(commentId) {
    openConfirmModal("Are you sure you want to delete this comment?", () => {
        fetch(`${API_BASE}/api/deleteComment`, {
            method: 'POST',
            body: JSON.stringify({ username: currentUser, commentId: commentId.toString() })
        })
        .then(res => res.text())
        .then(data => {
            if (data === "SUCCESS") {
                showToast("Comment deleted.");
                if (typeof loadComments === 'function') {
                    const urlParams = new URLSearchParams(window.location.search);
                    const postId = urlParams.get('id');
                    if (postId) loadComments(postId);
                } else {
                    const commentElem = document.getElementById(`comment-container-${commentId}`);
                    if (commentElem) commentElem.remove();
                }
            }
        });
    });
}

function toggleComments(postId) {
    const targetUrl = `post.html?id=${postId}`;
    window.location.href = targetUrl;
}

function feedFollowUser(buttonElement, targetUsername) {
    fetch(`${API_BASE}/api/toggleFollow`, {
        method: 'POST',
        body: JSON.stringify({ currentUser: currentUser, targetUser: targetUsername })
    })
    .then(response => response.text())
    .then(data => {
        if (data === "FOLLOWED") {
            buttonElement.textContent = "Unfollow";
            buttonElement.style.background = "rgba(255, 51, 102, 0.1)";
            buttonElement.style.color = "#ff3366";
        } else {
            buttonElement.textContent = "Follow";
            buttonElement.style.background = "";
            buttonElement.style.color = "";
        }
    })
    .catch(error => console.error("Error toggling follow:", error));
}

function toggleOverlayMenu() {
    const menu = document.getElementById('fullMenu');
    if (menu) menu.classList.toggle('open');
}

function highlightActiveNav() {
    let path = window.location.pathname;
    if (path === "/" || path === "" || path.endsWith("/")) path = "index.html";
    const currentPage = path.split("/").pop();
    const navLinks = document.querySelectorAll('.bottom-nav a, .overlay-links a');

    navLinks.forEach(link => {
        const linkTarget = link.getAttribute('href');
        if (currentPage === linkTarget) {
            link.classList.add('nav-link-active');
        } else {
            link.classList.remove('nav-link-active');
        }
    });
}

function checkNotifications() {
    if (!currentUser) return;
    fetch(`${API_BASE}/api/getNotifications`, {
        method: 'POST',
        body: JSON.stringify({ username: currentUser })
    })
    .then(res => res.json())
    .then(data => {
        const unreadCount = data.filter(n => n.isRead === false || n.isRead === 0).length;
        const bell = document.getElementById('nav-bell');
        if (bell) {
            let bellContent = `<span class="material-icons">notifications</span>`;
            if (unreadCount > 0) bellContent += `<span class="notification-badge">${unreadCount}</span>`;
            bell.innerHTML = bellContent;
        }
    })
    .catch(err => console.error("Notification Sync Error:", err));
}

function checkUnreadMessages() {
    if (!currentUser) return;
    fetch(`${API_BASE}/api/getUnreadCount`, {
        method: 'POST',
        body: JSON.stringify({ currentUser: currentUser })
    })
    .then(res => res.json())
    .then(data => {
        const mailBtn = document.getElementById('nav-mail');
        if (mailBtn) {
            const isMessagesPage = window.location.pathname.includes('messages.html');
            const colorStyle = isMessagesPage ? 'color: #00e676;' : '';
            let mailContent = `<span class="material-icons" style="${colorStyle}">mail</span>`;
            
            if (data.unread > 0) {
                mailContent += `<div style="position: absolute; top: 5px; right: 50%; transform: translateX(12px); width: 10px; height: 10px; background-color: #ff3366; border-radius: 50%; border: 2px solid #0f172a; z-index: 10;"></div>`;
            }
            
            mailBtn.style.position = 'relative'; 
            mailBtn.innerHTML = mailContent;
        }
    })
    .catch(err => console.error("Message Sync Error:", err));
}

/* ========================================= */
/* --- THE QUOTE & REPOST ENGINE (V2) ---    */
/* ========================================= */

function openRepostMenu(postId, isReposted = false, commentId = null) {
    let existingMenu = document.getElementById('repostMenuOverlay');
    if (existingMenu) existingMenu.remove();

    const overlay = document.createElement('div');
    overlay.id = 'repostMenuOverlay';
    overlay.className = 'modal-overlay';
    overlay.style.zIndex = '5000';
    overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };

    const menuCard = document.createElement('div');
    menuCard.className = 'modal-card';
    menuCard.style.maxWidth = '300px';

    const repostBtnConfig = isReposted 
        ? `<span class="material-icons" style="color: #ff3366;">undo</span> <span style="color: #ff3366;">Undo Repost</span>`
        : `<span class="material-icons">repeat</span> Repost Instantly`;
    
    const repostBgStyle = isReposted ? 'background: rgba(255, 51, 102, 0.1); border: 1px solid rgba(255, 51, 102, 0.3);' : '';

    menuCard.innerHTML = `
        <h3 style="margin-bottom: 20px;">Share</h3>
        <button class="main-post-btn" style="width: 100%; display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 10px; transition: 0.2s; ${repostBgStyle}" id="repostConfirm">
            ${repostBtnConfig}
        </button>
        <button class="media-control-btn" style="width: 100%; display: flex; align-items: center; justify-content: center; gap: 10px;" id="quoteConfirm">
            <span class="material-icons">format_quote</span> Quote this post
        </button>
    `;

    overlay.appendChild(menuCard);
    document.body.appendChild(overlay);

    document.getElementById('repostConfirm').onclick = () => { executeRepost(postId, isReposted, commentId); overlay.remove(); };
    document.getElementById('quoteConfirm').onclick = () => { openQuoteEditor(postId, commentId); overlay.remove(); };
}

// THE FIX: Extracted to the Global Scope so Kebab Menus can access it from anywhere!
window.openDynamicListModal = function(title, endpoint, bodyPayload) {
    let existing = document.getElementById('dynamicListModalOverlay');
    if (existing) existing.remove();

    const listOverlay = document.createElement('div');
    listOverlay.id = 'dynamicListModalOverlay';
    listOverlay.className = 'modal-overlay';
    listOverlay.style.zIndex = '6000';
    
    listOverlay.innerHTML = `
        <div class="modal-card" style="max-width: 500px; width: 98%; padding: 25px 15px; max-height: 85vh; display: flex; flex-direction: column;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h3 style="margin: 0; color: white;">${title}</h3>
                <button onclick="document.getElementById('dynamicListModalOverlay').remove()" style="background: transparent; border: none; color: #ff3366; cursor: pointer; display: flex; align-items: center;"><span class="material-icons">close</span></button>
            </div>
            <div id="dynamicListContainer" style="overflow-y: auto; flex: 1; padding-right: 10px;">
                <div style="text-align: center; margin-top: 20px; color: #00e676; display: flex; align-items: center; justify-content: center; gap: 8px;">
                    <span class="material-icons" style="animation: spin 1s linear infinite;">autorenew</span>
                    <span style="font-weight: bold;">Fetching...</span>
                </div>
            </div>
        </div>`;
    document.body.appendChild(listOverlay);

    fetch(`${API_BASE}${endpoint}`, {
        method: 'POST',
        body: JSON.stringify(bodyPayload)
    })
    .then(res => res.json())
    .then(data => {
        const container = document.getElementById('dynamicListContainer');
        if (data.length === 0) {
            container.innerHTML = `<p style="text-align: center; color: gray; margin-top: 20px;">Nothing to show yet.</p>`;
            return;
        }
        
        let html = "";
        data.forEach(item => {
            if (item.content !== undefined) {
                html += `
                <div style="background: rgba(255,255,255,0.05); padding: 15px; border-radius: 12px; margin-bottom: 10px; border-left: 3px solid #00e676; cursor: pointer;" onclick="window.location.href='post.html?id=${item.id}'">
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <img src="${item.avatar}" style="width: 35px; height: 35px; border-radius: 50%; object-fit: cover;">
                        <strong style="color: white;">${item.username}</strong>
                        <span style="color: gray; font-size: 12px; margin-left: auto;">${item.timestamp.substring(0,16)}</span>
                    </div>
                    <p style="margin-top: 10px; color: #e0e0e0; font-size: 14px;">${item.content}</p>
                    ${item.media ? `<div style="margin-top: 10px; color: #00e676; font-size: 12px;"><span class="material-icons" style="font-size:14px; vertical-align:middle;">image</span> Attached Media</div>` : ''}
                </div>`;
            } else {
                html += `
                <div style="display: flex; align-items: center; gap: 12px; padding: 12px; background: rgba(255,255,255,0.03); border-radius: 12px; margin-bottom: 10px; border: 1px solid rgba(255,255,255,0.05);">
                    <img src="${item.avatar}" onclick="window.location.href='profile.html?user=${item.username}'" style="width: 45px; height: 45px; border-radius: 50%; object-fit: cover; cursor: pointer; flex-shrink: 0;">
                    <div style="flex: 1; cursor: pointer; min-width: 0; display: flex; flex-direction: column;" onclick="window.location.href='profile.html?user=${item.username}'">
                        <strong class="username-truncate" style="color: white; display: block; max-width: 100%;">${item.username}</strong>
                        <span class="truncate-text" style="font-size: 11px; color: #a09eb5;">${item.bio || "No bio yet."}</span>
                    </div>
                </div>`;
            }
        });
        container.innerHTML = html;
    });
};

window.openQuotesModal = function(postId) {
    openDynamicListModal("Quoted By", "/api/getPostQuotes", { postId: postId.toString(), currentUser: currentUser });
};

function openQuoteEditor(postId, commentId = null) {
    let existingMenu = document.getElementById('quoteMenuOverlay');
    if (existingMenu) existingMenu.remove();

    let quoteMediaBase64 = "";

    const overlay = document.createElement('div');
    overlay.id = 'quoteMenuOverlay';
    overlay.className = 'modal-overlay';
    overlay.style.zIndex = '5000';
    overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };

    const menuCard = document.createElement('div');
    menuCard.className = 'modal-card';
    menuCard.innerHTML = `
        <h3>Quote this post</h3>
        <textarea id="quoteTextArea" class="bio-edit-input" style="height:100px; margin: 20px 0;" placeholder="Add your thoughts..."></textarea>
        
        <div id="quoteMediaPreview" class="media-preview-container"></div>
        
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;">
            <div style="display: flex; gap: 10px;">
                <input type="file" id="quoteImgUpload" accept="image/*" hidden>
                <input type="file" id="quoteVidUpload" accept="video/*" hidden>
                <label for="quoteImgUpload" class="icon-btn" style="cursor: pointer;"><span class="material-icons">image</span></label>
                <label for="quoteVidUpload" class="icon-btn" style="cursor: pointer;"><span class="material-icons">videocam</span></label>
            </div>
        </div>

        <div class="modal-buttons">
            <button class="modal-cancel-btn" id="cancelQuoteBtn">Cancel</button>
            <button class="main-post-btn" id="submitQuote">Quote</button>
        </div>
    `;

    overlay.appendChild(menuCard);
    document.body.appendChild(overlay);
    
    document.getElementById('cancelQuoteBtn').onclick = () => overlay.remove();

    const handleQuoteMedia = (input, type) => {
        const file = input.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onloadend = function() {
                quoteMediaBase64 = reader.result;
                const preview = document.getElementById('quoteMediaPreview');
                if (type === 'image') {
                    preview.innerHTML = `<div class="preview-item"><img src="${quoteMediaBase64}"><button class="remove-media-btn" id="rmQuoteMedia">X</button></div>`;
                } else {
                    preview.innerHTML = `<div class="preview-item"><video src="${quoteMediaBase64}" controls style="max-height: 200px; border-radius: 10px;"></video><button class="remove-media-btn" id="rmQuoteMedia">X</button></div>`;
                }
                document.getElementById('rmQuoteMedia').onclick = () => {
                    quoteMediaBase64 = "";
                    preview.innerHTML = "";
                    input.value = "";
                };
            };
            reader.readAsDataURL(file);
        }
    };

    document.getElementById('quoteImgUpload').onchange = function() { handleQuoteMedia(this, 'image'); };
    document.getElementById('quoteVidUpload').onchange = function() { handleQuoteMedia(this, 'video'); };

    const textArea = document.getElementById('quoteTextArea');
    textArea.focus();

    document.getElementById('submitQuote').onclick = () => {
        if (textArea.value.trim() === "" && quoteMediaBase64 === "") {
            showToast("Please add text or media to your quote.");
            return;
        }

        // THE FIX: Contextually target the exact active timeline container to isolate layout contexts!
        let feedElement = null;
        if (window.location.pathname.includes('index.html')) feedElement = document.getElementById("feedContainer");
        else if (window.location.pathname.includes('profile.html')) feedElement = document.getElementById("profileFeedContainer");
        else if (window.location.pathname.includes('top.html')) feedElement = document.getElementById("topFeedContainer");
        else if (window.location.pathname.includes('saved.html')) feedElement = document.getElementById("feed");
        else feedElement = document.getElementById("feedContainer");

        if (feedElement) {
            const tempHTML = `
            <div class="post" style="opacity: 0.7; border-left: 3px solid #00e676; margin-bottom: 20px;">
                <div style="display: flex; gap: 10px; align-items: center;">
                    <span class="material-icons" style="color: #00e676;">format_quote</span>
                    <strong style="color: white;">Processing Quote...</strong>
                </div>
                <p style="color: #e0e0e0; margin-top: 10px;">${parseSocialText(textArea.value)}</p>
            </div>`;
            feedElement.insertAdjacentHTML('afterbegin', tempHTML);
        }
        
        if (typeof executeQuote === 'function') {
            executeQuote(postId, textArea.value, quoteMediaBase64, commentId);
        } else {
            fetch(`${API_BASE}/api/createPost`, {
                method: 'POST',
                body: JSON.stringify({ 
                    username: currentUser, 
                    content: textArea.value, 
                    media: quoteMediaBase64, 
                    parentPostId: postId,
                    parentCommentId: commentId 
                })
            })
            .then(res => res.text())
            .then(data => {
                if(data === "SUCCESS") {
                    showToast("Quote Posted!");
                    currentPage = 1;
                    if (typeof loadFeed === 'function' && document.getElementById("feedContainer")) { document.getElementById("feedContainer").innerHTML = ""; loadFeed(); }
                    else if (typeof loadUserPosts === 'function' && document.getElementById("profileFeedContainer")) { document.getElementById("profileFeedContainer").innerHTML = ""; loadUserPosts(); }
                    else if (typeof fetchTopPosts === 'function' && document.getElementById("topFeedContainer")) fetchTopPosts();
                    else if (typeof loadSavedData === 'function' && document.getElementById("feed")) { document.getElementById("feed").innerHTML = ""; loadSavedData(); }
                    else if (typeof fetchSinglePost === 'function' && currentPostId) fetchSinglePost(currentPostId);
                }
            });
        }
        overlay.remove();
    };
}

function executeRepost(postId, isCurrentlyReposted, commentId = null) {
    // THE FIX: Contextually target the exact active timeline container to isolate layout contexts!
    let feedElement = null;
    if (window.location.pathname.includes('index.html')) feedElement = document.getElementById("feedContainer");
    else if (window.location.pathname.includes('profile.html')) feedElement = document.getElementById("profileFeedContainer");
    else if (window.location.pathname.includes('top.html')) feedElement = document.getElementById("topFeedContainer");
    else if (window.location.pathname.includes('saved.html')) feedElement = document.getElementById("feed");
    else feedElement = document.getElementById("feedContainer");

    if (!isCurrentlyReposted && feedElement) {
        const tempHTML = `
        <div class="post" style="opacity: 0.7; border-left: 3px solid #00a8ff; margin-bottom: 20px;">
            <div style="display: flex; gap: 10px; align-items: center;">
                <span class="material-icons" style="color: #00a8ff;">repeat</span>
                <strong style="color: white;">Processing Repost...</strong>
            </div>
        </div>`;
        feedElement.insertAdjacentHTML('afterbegin', tempHTML);
    }

    fetch(`${API_BASE}/api/createPost`, {
        method: 'POST',
        body: JSON.stringify({ 
            username: currentUser, 
            content: "", 
            media: "", 
            parentPostId: postId,
            parentCommentId: commentId 
        })
    })
    .then(response => response.text())
    .then(data => {
        if (data === "SUCCESS") {
            showToast(isCurrentlyReposted ? "Undo Repost Successful" : "Reposted!");
            if (!isCurrentlyReposted) {
                currentPage = 1;
                if (typeof loadFeed === 'function' && document.getElementById("feedContainer")) { document.getElementById("feedContainer").innerHTML = ""; loadFeed(); }
                else if (typeof loadUserPosts === 'function' && document.getElementById("profileFeedContainer")) { document.getElementById("profileFeedContainer").innerHTML = ""; loadUserPosts(); }
                else if (typeof fetchTopPosts === 'function' && document.getElementById("topFeedContainer")) fetchTopPosts();
                else if (typeof loadSavedData === 'function' && document.getElementById("feed")) { document.getElementById("feed").innerHTML = ""; loadSavedData(); }
            }
        }
    });

    // OPTIMISTIC UI: SYNC ALL REPOST BUTTONS INSTANTLY
    const targetBtns = document.querySelectorAll(commentId ? `[id="comment-repost-btn-${commentId}"]` : `[id="repost-btn-${postId}"]`);
    targetBtns.forEach(btn => {
        const countSpan = btn.querySelector('.repost-count');
        let currentCount = countSpan ? (parseInt(countSpan.textContent) || 0) : 0;
        
        if (isCurrentlyReposted) {
            btn.style.background = '';
            btn.style.color = '';
            if (countSpan) countSpan.textContent = currentCount > 0 ? currentCount - 1 : 0;
            btn.setAttribute('onclick', `event.stopPropagation(); openRepostMenu(${postId}, false, ${commentId || 'null'})`);
        } else {
            btn.style.background = 'rgba(0, 168, 255, 0.1)';
            btn.style.color = '#00a8ff';
            if (countSpan) countSpan.textContent = currentCount + 1;
            btn.setAttribute('onclick', `event.stopPropagation(); openRepostMenu(${postId}, true, ${commentId || 'null'})`);
        }
    });
}

// [THE FIX]: Pushed the timestamp to the bottom right using flex-end layout
function buildQuoteBox(parentPost) {
    if (!parentPost) return ""; 
    
    // Safety routing through the Grid Engine prevents the Array TypeError crash!
    let pMediaHTML = typeof generateMediaGridHTML === 'function' ? generateMediaGridHTML(parentPost.media, parentPost.id, 'quote') : "";

    const pEditedTag = parentPost.isEdited ? `<span style="font-size: 11px; color: #6a6680; font-style: italic; margin-left: 5px; flex-shrink: 0;"> (edited)</span>` : "";
    const linkTarget = parentPost.isComment ? `post.html?id=${parentPost.postId}&commentId=${parentPost.id}` : `post.html?id=${parentPost.id}`;

    const shortTime = parentPost.timestamp && String(parentPost.timestamp).length > 16 ? String(parentPost.timestamp).substring(0, 16) : (parentPost.timestamp || "");

    return `
    <div class="embedded-quote" onclick="event.stopPropagation(); window.location.href='${linkTarget}'" style="border: 1px solid #334155; border-radius: 12px; padding: 12px; margin-top: 10px; background: rgba(255,255,255,0.02); cursor: pointer; transition: background 0.2s;">
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px; min-width: 0;">
            <img src="${parentPost.avatar}" style="width: 20px; height: 20px; border-radius: 50%; object-fit: cover; flex-shrink: 0;">
            <strong class="username-truncate" style="font-size: 13px; color: white;">${parentPost.username}</strong>
            ${pEditedTag}
        </div>
        <div style="font-size: 14px; color: #e0e0e0; line-height: 1.4;">${typeof parseSocialText === "function" ? parseSocialText(parentPost.content) : (parentPost.content || "")}</div>
        ${pMediaHTML}
        
        <div style="display: flex; justify-content: flex-end; margin-top: 8px;">
            <span style="font-size: 11px; color: gray;">${shortTime}</span>
        </div>
    </div>
    `;
}

/* ========================================= */
/* --- THE GLOBAL LIGHTBOX INTERCEPTOR ---   */
/* ========================================= */
function openLightbox(imageSrc) {
    let overlay = document.getElementById('customLightboxOverlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'customLightboxOverlay';
        overlay.className = 'modal-overlay';
        overlay.style.zIndex = '9999';
        overlay.style.backgroundColor = 'rgba(0,0,0,0.9)'; 
        overlay.style.display = 'none';
        overlay.style.flexDirection = 'column';
        overlay.style.justifyContent = 'center';
        overlay.style.alignItems = 'center';
        overlay.innerHTML = `
            <div style="width: 100%; padding: 20px; display: flex; justify-content: flex-end; position: absolute; top: 0; right: 0; box-sizing: border-box;">
                <span class="material-icons" style="color: white; font-size: 40px; cursor: pointer; background: rgba(255, 51, 102, 0.8); border-radius: 50%; padding: 5px; transition: 0.2s;" onmouseover="this.style.background='#ff3366'" onmouseout="this.style.background='rgba(255, 51, 102, 0.8)'" onclick="closeLightbox()">close</span>
            </div>
            <img id="lightboxImg" src="" style="max-width: 95%; max-height: 90vh; border-radius: 10px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); object-fit: contain;">
        `;
        document.body.appendChild(overlay);
    }
    document.getElementById('lightboxImg').src = imageSrc;
    overlay.style.display = 'flex';
}

function closeLightbox() {
    const overlay = document.getElementById('customLightboxOverlay');
    if (overlay) overlay.style.display = 'none';
}

/* ========================================== */
/* --- GLOBAL SCROLL MEMORY ENGINE            */
/* ========================================== */
function saveScrollPosition(pageIdentifier = 'general') {
    sessionStorage.setItem(pageIdentifier + 'Scroll', window.scrollY || document.documentElement.scrollTop);
}

function restoreScrollPosition(pageIdentifier = 'general') {
    setTimeout(() => {
        const savedScroll = sessionStorage.getItem(pageIdentifier + 'Scroll');
        if (savedScroll) {
            window.scrollTo(0, parseInt(savedScroll));
            sessionStorage.removeItem(pageIdentifier + 'Scroll');
        }
    }, 100); 
}

/* ========================================= */
/* --- KEBAB MENU & SAVE POST ENGINE ---     */
/* ========================================= */

// Toggles the specific dropdown and closes all others
window.toggleDropdown = function(dropdownId) {
    document.querySelectorAll('.dropdown-menu').forEach(menu => {
        if (menu.id !== dropdownId) menu.style.display = 'none';
    });
    
    const menu = document.getElementById(dropdownId);
    if (menu) {
        menu.style.display = menu.style.display === 'flex' ? 'none' : 'flex';
    }
};

// Global click listener to close dropdowns if user clicks anywhere else on the screen
document.addEventListener('click', function(event) {
    if (!event.target.closest('.kebab-menu-container')) {
        document.querySelectorAll('.dropdown-menu').forEach(menu => {
            menu.style.display = 'none';
        });
    }
});

// The Save Post logic
// The Live Save Post Logic
/* ========================================= */
/* --- UNIVERSAL KEBAB & OPTIMISTIC UI ---   */
/* ========================================= */

// The Live Optimistic Save Engine
window.savePost = function(id, btnElement, type = 'post') {
    const isUnsaving = btnElement.innerText.includes('Unsave');
    const endpoint = type === 'comment' ? '/api/saveComment' : '/api/savePost';
    
    // 1. OPTIMISTIC UI INSTANT SWAP
    // 1. OPTIMISTIC UI INSTANT SWAP
    // 1. OPTIMISTIC UI INSTANT SWAP
    if (isUnsaving) {
        btnElement.innerHTML = `<span class="material-icons" style="font-size: 18px;">bookmark_border</span> Save`;
        if (typeof showToast === "function") showToast(`${type === 'comment' ? 'Comment' : 'Post'} Removed!`);
        
        // [THE FIX]: Now vanishes BOTH posts and comments!
        if (window.location.pathname.includes('saved.html')) {
            const cardId = type === 'comment' ? `comment-card-${id}` : `post-card-${id}`;
            const card = document.getElementById(cardId);
            if (card) card.style.display = 'none';
        }
    } else {
        btnElement.innerHTML = `<span class="material-icons" style="font-size: 18px;">bookmark</span> Unsave`;
        if (typeof showToast === "function") showToast(`${type === 'comment' ? 'Comment' : 'Post'} Saved!`);
    }

    // Close dropdown instantly
    document.querySelectorAll('.dropdown-menu').forEach(menu => menu.style.display = 'none');

    // 3. SILENT BACKEND SYNC
    fetch(`${API_BASE}${endpoint}`, {
        method: 'POST',
        body: JSON.stringify({ currentUser: currentUser, postId: id.toString(), commentId: id.toString() })
    }).catch(err => console.error("Save Error:", err));
};

// THE FIX: The Profile Pinned Post Engine (Database Sync + Local Cache)
window.toggleProfilePin = function(id) {
    let currentPin = localStorage.getItem('pinnedProfilePost_' + currentUser);
    let isPinning = currentPin !== String(id);

    if (!isPinning) {
        localStorage.removeItem('pinnedProfilePost_' + currentUser);
        if (typeof showToast === "function") showToast("Post unpinned from profile");
    } else {
        localStorage.setItem('pinnedProfilePost_' + currentUser, String(id));
        if (typeof showToast === "function") showToast("Post pinned to profile!");
    }
    document.querySelectorAll('.dropdown-menu').forEach(menu => menu.style.display = 'none');
    
    // THE FIX: Sync with the backend database so other users can see your pinned post!
    fetch(`${API_BASE}/api/togglePin`, {
        method: 'POST',
        body: JSON.stringify({ username: currentUser, postId: isPinning ? String(id) : null })
    }).catch(err => console.warn("Silent Pin Sync", err));

    // Auto-reload the profile feed if they are currently looking at it
    if (window.location.pathname.includes('profile.html') && typeof showAllPosts === 'function') {
        loadUserPosts();
    }
};

// The Universal Kebab Generator (DRY Architecture)
window.generateKebabMenu = function(item, type = 'post') {
    const id = type === 'comment' ? item.id : item.id;
    const isSaved = item.isSaved || false;
    const isFollowing = item.isFollowing || false;
    const contentEscaped = item.content ? String(item.content).replace(/'/g, "\\'") : "";
   const itemOwner = item.username;
    
    let kebabHTML = "";
    if (itemOwner === currentUser) {
        kebabHTML = `
            <div class="kebab-menu-container">
                <button class="kebab-btn" onclick="event.stopPropagation(); toggleDropdown('dropdown-${type}-${id}')">
                    <span class="material-icons">more_vert</span>
                </button>
                <div id="dropdown-${type}-${id}" class="dropdown-menu">
                    <button class="dropdown-item" onclick="event.stopPropagation(); ${type === 'comment' ? `editComment(${id}, '${contentEscaped}')` : `editPost(${id}, '${contentEscaped}')`}">
                        <span class="material-icons" style="font-size: 18px;">edit</span> Edit
                    </button>
                    <button class="dropdown-item danger" onclick="event.stopPropagation(); ${type === 'comment' ? `deleteComment(${id})` : `deletePost(${id})`}">
                        <span class="material-icons" style="font-size: 18px;">delete</span> Delete
                    </button>
                    <button class="dropdown-item" onclick="event.stopPropagation(); savePost(${id}, this, '${type}')">
                        <span class="material-icons" style="font-size: 18px;">${isSaved ? 'bookmark' : 'bookmark_border'}</span> ${isSaved ? 'Unsave' : 'Save'}
                    </button>
                    <button class="dropdown-item" onclick="event.stopPropagation(); toggleDropdown('dropdown-${type}-${id}'); openDynamicListModal('Liked By', '/api/get${type === 'comment' ? 'Comment' : 'Post'}Likes', { ${type === 'comment' ? 'commentId' : 'postId'}: '${id}' })">
                        <span class="material-icons" style="font-size: 18px;">favorite</span> Liked by
                    </button>
                    <button class="dropdown-item" onclick="event.stopPropagation(); toggleDropdown('dropdown-${type}-${id}'); openDynamicListModal('Reposted By', '/api/get${type === 'comment' ? 'Comment' : 'Post'}Reposts', { ${type === 'comment' ? 'commentId' : 'postId'}: '${id}' })">
                        <span class="material-icons" style="font-size: 18px;">repeat</span> Reposted by
                    </button>
                    ${type === 'post' ? `
                    <button class="dropdown-item" onclick="event.stopPropagation(); toggleProfilePin(${id})">
                        <span class="material-icons" style="font-size: 18px; transform: rotate(45deg);">push_pin</span> ${localStorage.getItem('pinnedProfilePost_' + currentUser) === String(id) ? 'Unpin from Profile' : 'Pin to Profile'}
                    </button>
                    ` : ''}
                </div>
            </div>
        `;
    } else {
        const followText = isFollowing ? 'Unfollow' : 'Follow';
        const followColor = isFollowing ? 'color: #ff3366; background: rgba(255, 51, 102, 0.05);' : 'color: white;';
        
        kebabHTML = `
            <div class="kebab-menu-container">
                <button class="kebab-btn" onclick="event.stopPropagation(); toggleDropdown('dropdown-${type}-${id}')">
                    <span class="material-icons">more_vert</span>
                </button>
                <div id="dropdown-${type}-${id}" class="dropdown-menu">
                    <button class="dropdown-item" onclick="event.stopPropagation(); savePost(${id}, this, '${type}')">
                        <span class="material-icons" style="font-size: 18px;">${isSaved ? 'bookmark' : 'bookmark_border'}</span> ${isSaved ? 'Unsave' : 'Save'}
                    </button>
                    <button class="dropdown-item" style="${followColor}" onclick="event.stopPropagation(); ${type === 'comment' ? 'feedFollowUser' : 'feedFollowUser'}(this, '${itemOwner}'); toggleDropdown('dropdown-${type}-${id}');">
                        ${followText}
                    </button>
                </div>
            </div>
        `;
    }
    return kebabHTML;
}

/* ========================================= */
/* --- GOOGLE OAUTH 2.0 FRONTEND LOGIC ---   */
/* ========================================= */

let tempNewUsername = "";

function handleGoogleLogin(response) {
    fetch(`${API_BASE}/api/googleLogin`, {
        method: 'POST',
        body: response.credential
    })
    .then(res => res.text())
    .then(username => {
        if (username.startsWith("NEW_USER:")) {
            tempNewUsername = username.split(":")[1];
            localStorage.setItem("currentUser", tempNewUsername);
            
            const modal = document.getElementById('newUsernameModal');
            if (modal) {
                document.getElementById('generatedUsernameDisplay').textContent = "@" + tempNewUsername;
                modal.style.display = 'flex';
            } else {
                window.location.href = "index.html"; 
            }
        } else if (username !== "ERROR") {
            localStorage.setItem("currentUser", username);
            window.location.href = "index.html"; 
        } else {
            showToast("Google Authentication Failed. Please try again.");
        }
    })
    .catch(err => {
        console.error("Google Login Error: ", err);
        showToast("Network error during Google Login.");
    });
}

function keepUsername() {
    window.location.href = "index.html";
}

function showCustomUsernameInput() {
    document.getElementById('modalActionButtons').style.display = 'none';
    document.getElementById('customUsernameSection').style.display = 'block';
    document.getElementById('modalSaveButtons').style.display = 'flex';
    document.getElementById('customUsernameInput').value = tempNewUsername;
    document.getElementById('customUsernameInput').focus();
}

function saveCustomUsername() {
    const newName = document.getElementById('customUsernameInput').value.trim();
    
    if (newName === "" || newName === tempNewUsername) {
        window.location.href = "index.html";
        return;
    }
    
    fetch(`${API_BASE}/api/updateUsername`, {
        method: 'POST',
        body: JSON.stringify({ currentUsername: tempNewUsername, newUsername: newName })
    })
    .then(res => res.text())
    .then(data => {
        if (data === "SUCCESS") {
            localStorage.setItem("currentUser", newName); 
            window.location.href = "index.html";
        } else {
            document.getElementById('usernameError').style.display = 'block';
        }
    });
}

/* ========================================= */
/* --- THE VOICE NOTE ENGINE (HTML5) ---     */
/* ========================================= */
let mediaRecorder;
let audioChunks = [];
let isRecordingVoice = false;
let voiceRecordTimer;
let voiceRecordSeconds = 0;

window.startVoiceRecord = async function(context) {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        mediaRecorder = new MediaRecorder(stream);
        audioChunks = [];

        mediaRecorder.ondataavailable = event => {
            if (event.data.size > 0) audioChunks.push(event.data);
        };

        mediaRecorder.onstop = () => {
            const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
            const reader = new FileReader();
            reader.readAsDataURL(audioBlob);
            reader.onloadend = () => {
                if (context === 'message' && typeof attachMsgVoiceNote === 'function') {
                    attachMsgVoiceNote(reader.result);
                }
            };
            stream.getTracks().forEach(track => track.stop());
        };

        mediaRecorder.start();
        isRecordingVoice = true;
        voiceRecordSeconds = 0;
        
        if (context === 'message') {
            const micBtn = document.getElementById('micRecordBtn');
            const inputArea = document.getElementById('messageInput');
            if(micBtn) {
                micBtn.style.color = '#ff3366';
                micBtn.classList.add('recording-pulse');
            }
            if(inputArea) {
                voiceRecordTimer = setInterval(() => {
                    voiceRecordSeconds++;
                    const mins = Math.floor(voiceRecordSeconds / 60);
                    const secs = voiceRecordSeconds % 60;
                    inputArea.placeholder = `Recording... ${mins}:${secs < 10 ? '0' : ''}${secs}`;
                }, 1000);
            }
        }
    } catch (err) {
        console.error("Microphone Access Denied:", err);
        if (typeof showToast === 'function') showToast("Microphone access denied. Please check browser permissions.");
    }
};

window.stopVoiceRecord = function(context) {
    if (mediaRecorder && isRecordingVoice) {
        mediaRecorder.stop();
        isRecordingVoice = false;
        clearInterval(voiceRecordTimer);
        
        if (context === 'message') {
            const micBtn = document.getElementById('micRecordBtn');
            const inputArea = document.getElementById('messageInput');
            if(micBtn) {
                micBtn.style.color = '#a09eb5';
                micBtn.classList.remove('recording-pulse');
            }
            if(inputArea) inputArea.placeholder = "Start a new message...";
        }
    }
};

window.toggleVoiceRecord = function(context) {
    if (isRecordingVoice) stopVoiceRecord(context);
    else startVoiceRecord(context);
};

/* ========================================= */
/* --- THE SOCIAL TEXT PARSER (REGEX) ---    */
/* ========================================= */
function parseSocialText(text) {
    if (!text) return "";
    
    let html = String(text).replace(/\\n/g, '<br>').replace(/\n/g, '<br>');
    
    // THE FIX: Swapped #00a8ff for rgb() so the Hashtag parser below doesn't shatter the HTML!
    html = html.replace(/(https?:\/\/[^\s<]+)/g, `<a href="$1" target="_blank" style="color: rgb(0, 168, 255); text-decoration: underline;" onclick="event.stopPropagation();">$1</a>`);
    html = html.replace(/@([\w_]+)/g, `<a href="profile.html?user=$1" style="color: rgb(0, 168, 255); text-decoration: none; font-weight: bold;" onclick="event.stopPropagation();">@$1</a>`);
    html = html.replace(/#([\w_]+)/g, `<a href="search.html?q=%23$1" style="color: rgb(0, 230, 118); text-decoration: none; font-weight: bold;" onclick="event.stopPropagation();">#$1</a>`);
    
    return html;
}

const nativeRequestFullscreen = Element.prototype.requestFullscreen || Element.prototype.webkitRequestFullscreen;
Element.prototype.requestFullscreen = function() {
    if (this.tagName && this.tagName.toLowerCase() === 'img') {
        openLightbox(this.src); 
        return Promise.resolve();
    }
    if (nativeRequestFullscreen) {
        return nativeRequestFullscreen.call(this); 
    }
};

/* ========================================= */
/* --- THE UNIVERSAL 2x2 MEDIA GRID ENGINE   */
/* ========================================= */
window.generateMediaGridHTML = function(mediaData, postId, context = 'feed') {
    if (!mediaData) return "";
    if (typeof mediaData === 'string' && mediaData.trim() === "") return "";
    if (Array.isArray(mediaData) && mediaData.length === 0) return "";

    let mediaArray = [];
    try {
        if (typeof mediaData === 'string') {
            if (mediaData.startsWith("[")) mediaArray = JSON.parse(mediaData);
            else mediaArray = [mediaData]; 
        } else if (Array.isArray(mediaData)) {
            mediaArray = mediaData;
        }
    } catch (e) { mediaArray = [mediaData]; }
    
    // Strict Type-Checking: Filter out nulls to prevent crashes
    mediaArray = mediaArray.filter(item => item && typeof item === 'string' && item.trim() !== "");
    if (mediaArray.length === 0) return "";

    let gridClass = "media-grid-1";
    if (mediaArray.length === 2) gridClass = "media-grid-2";
    if (mediaArray.length === 3) gridClass = "media-grid-3";
    if (mediaArray.length >= 4) { gridClass = "media-grid-4"; mediaArray = mediaArray.slice(0, 4); } // Hard cap at 4

    let html = `<div class="media-gallery ${gridClass}">`;

    mediaArray.forEach((item, index) => {
        // THE FIX: Protect raw Base64 'data:' strings from being corrupted by getFullMediaUrl
        const isBase64 = item.startsWith("data:");
        const mediaUrl = isBase64 ? item : (typeof getFullMediaUrl === 'function' ? getFullMediaUrl(item) : item);
        
        const ext = item.split('.').pop() || "png";
        const isVideo = item.toLowerCase().endsWith(".mp4") || item.startsWith("data:video");
        
        html += `<div class="media-cell">`;
        if (isVideo) {
            // THE FIX: Restored native thumbnails. #t=0.1 safely triggers a thumbnail for server files, but is stripped from Base64 to prevent corruption!
            const finalVideoSrc = isBase64 ? mediaUrl : mediaUrl + "#t=0.1";
            html += `
            <video 
                src="${finalVideoSrc}" 
                preload="metadata" 
                controls 
                controlsList="nodownload noplaybackrate nofullscreen noremoteplayback"
                disablePictureInPicture
                onclick="event.stopPropagation();" 
                class="grid-media-item" 
                style="background: #0b0f1a; object-fit: contain;">
            </video>`;
        } else {
            // Prevent lightbox from opening while in preview mode
            html += `<img src="${mediaUrl}" onclick="event.stopPropagation(); ${context !== 'preview' ? `openLightbox('${mediaUrl}')` : ''}" class="grid-media-item">`;
        }

        if (context === 'preview') {
            // THE FIX: Inject specific "X" button for each individual media item during Post Creation
            html += `<button type="button" onclick="event.stopPropagation(); window.removeSpecificMedia(${index})" style="position: absolute; top: 5px; right: 5px; z-index: 20; width: 28px; height: 28px; font-size: 14px; padding: 0; background: rgba(255,51,102,0.9); color: white; border: none; border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center;" title="Remove this item">X</button>`;
        } else if (context !== 'chat') {
            // THE FIX: Lock custom buttons strictly to the TOP RIGHT corner for all media types!
            html += `<div class="grid-controls" style="position: absolute; top: 10px; right: 10px; display: flex; gap: 8px; z-index: 10;">`;
            html += `<button onclick="event.stopPropagation(); window.NativeStorage ? window.NativeStorage.saveFile('${mediaUrl}', 'Media_${postId}_${index}_' + Date.now() + '.${ext}') : window.forceDownload('${mediaUrl}', 'Media_${postId}_${index}_' + Date.now() + '.${ext}')" class="media-control-btn" style="width: 35px!important; height: 35px!important; padding: 0!important; display: flex; justify-content: center; align-items: center; border-radius: 50%; background: rgba(0,0,0,0.6); border: 1px solid #00e676; color: #00e676;" title="Download"><span class="material-icons" style="font-size: 16px;">download</span></button>`;
            html += `<button onclick="event.stopPropagation(); this.parentElement.previousElementSibling.requestFullscreen()" class="media-control-btn" style="width: 35px!important; height: 35px!important; padding: 0!important; display: flex; justify-content: center; align-items: center; border-radius: 50%; background: rgba(0,0,0,0.6); border: 1px solid #00e676; color: #00e676;" title="Full Screen"><span class="material-icons" style="font-size: 18px;">fullscreen</span></button>`;
            html += `</div>`;
        }
        html += `</div>`;
    });
    html += `</div>`;
    return html;
};

checkNotifications();
checkUnreadMessages(); 
highlightActiveNav();
setInterval(checkNotifications, 30000);
setInterval(checkUnreadMessages, 30000);

/* ========================================= */
/* --- ACTION-TRIGGERED OFFLINE INTERCEPTOR --- */
/* ========================================= */
window.showGlobalOfflineModal = function() {
    let offlineModal = document.getElementById('globalOfflineModal');
    if (!offlineModal) {
        offlineModal = document.createElement('div');
        offlineModal.id = 'globalOfflineModal';
        offlineModal.className = 'modal-overlay';
        offlineModal.style.zIndex = '100000'; 
        offlineModal.style.backgroundColor = 'rgba(11, 15, 26, 0.95)';
        offlineModal.innerHTML = `
            <div class="modal-card" style="border-color: #ff3366; box-shadow: 0 15px 50px rgba(255, 51, 102, 0.2);">
                <div class="modal-icon"><span class="material-icons" style="font-size: 60px; color: #ff3366;">wifi_off</span></div>
                <h3 style="color: white; margin-top: 10px;">Connection Lost</h3>
                <p style="color: #a09eb5; margin: 15px 0;">We couldn't reach the server. Please check your network connection and try again.</p>
                <button class="modal-confirm-btn" style="background: #ff3366; color: white; width: 100%; border: none; box-shadow: 0 4px 15px rgba(255, 51, 102, 0.3);" onclick="document.getElementById('globalOfflineModal').style.display='none';">Okay</button>
            </div>
        `;
        document.body.appendChild(offlineModal);
    }
    offlineModal.style.display = 'flex';
};

// THE FIX: Upgraded Fetch Interceptor that ignores silent background loops!
const originalFetch = window.fetch;
window.fetch = async function(...args) {
    const url = args[0];
    const isBackgroundActivity = typeof url === 'string' && (url.includes('/getNotifications') || url.includes('/getUnreadCount'));

    if (!navigator.onLine && !isBackgroundActivity) {
        window.showGlobalOfflineModal();
        return Promise.reject(new Error("Offline"));
    }
    try {
        const response = await originalFetch(...args);
        return response;
    } catch (error) {
        if (!isBackgroundActivity && (error.name === 'TypeError' || error.message === 'Failed to fetch')) {
            window.showGlobalOfflineModal();
        }
        throw error;
    }
};

/* ========================================= */
/* --- THE AUTO-SVG ICON INJECTOR ---        */
/* ========================================= */
const svgIcons = {
    "home": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg>`,
    "search": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>`,
    "mail": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/></svg>`,
    "notifications": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/></svg>`,
    "person": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>`,
    "menu": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/></svg>`,
    "close": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>`,
    "more_vert": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>`,
    "favorite": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>`,
    "repeat": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"/></svg>`,
    "chat_bubble_outline": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/></svg>`,
    "chat": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/></svg>`,
    "push_pin": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z"/></svg>`,
    "edit": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>`,
    "delete": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>`,
    "bookmark": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>`,
    "bookmark_border": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z"/></svg>`,
    "photo_camera": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 12c1.65 0 3-1.35 3-3s-1.35-3-3-3-3 1.35-3 3 1.35 3 3 3zm0-4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm6-4h-3.17L13 2H11L9.17 4H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H6V6h3.17l1.83-2h2l1.83 2H18v12z"/></svg>`,
    "image": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/></svg>`,
    "videocam": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/></svg>`,
    "warning": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>`,
    "arrow_forward": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/></svg>`,
    "format_quote": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M6 17h3l2-4V7H5v6h3zm8 0h3l2-4V7h-6v6h3z"/></svg>`,
    "undo": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C20.08 11.03 16.63 8 12.5 8z"/></svg>`,
    "block": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9C4.63 15.55 4 13.85 4 12zm8 8c-1.85 0-3.55-.63-4.9-1.69L18.31 7.1C19.37 8.45 20 10.15 20 12c0 4.42-3.58 8-8 8z"/></svg>`,
    "lock": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>`,
    "lock_open": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z"/></svg>`,
    "autorenew": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M12 6v3l4-4-4-4v3c-4.42 0-8 3.58-8 8 0 1.57.46 3.03 1.24 4.26L6.7 14.8c-.45-.83-.7-1.79-.7-2.8 0-3.31 2.69-6 6-6zm6.76 1.74L17.3 9.2c.44.84.7 1.79.7 2.8 0 3.31-2.69 6-6 6v-3l-4 4 4 4v-3c4.42 0 8-3.58 8-8 0-1.57-.46-3.03-1.24-4.26z"/></svg>`,
    "download": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>`,
    "fullscreen": `<svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>`
};

function replaceIconsWithSVG() {
    document.querySelectorAll('.material-icons').forEach(el => {
        const iconName = el.textContent.trim();
        if (svgIcons[iconName]) {
            const spanContent = el.innerHTML;
            if (!spanContent.includes('<svg')) {
                el.innerHTML = svgIcons[iconName];
                el.style.display = 'inline-flex';
                el.style.alignItems = 'center';
                el.style.justifyContent = 'center';
            }
        }
    });
}

document.addEventListener("DOMContentLoaded", replaceIconsWithSVG);
const iconObserver = new MutationObserver((mutations) => {
    let shouldUpdate = false;
    mutations.forEach(m => { if (m.addedNodes.length > 0) shouldUpdate = true; });
    if (shouldUpdate) replaceIconsWithSVG();
});
iconObserver.observe(document.body, { childList: true, subtree: true });