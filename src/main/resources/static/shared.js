/* ========================================= */
/* --- SHARED SOCIAL CORE (V1.9 - X-STYLE ARCHITECTURE) --- */
/* ========================================= */

const API_BASE = "https://socialappwebsite.me";

// THE FIX: The security guard now correctly looks for the "currentUser" badge!
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
        .skel-avatar { width: 40px; height: 40px; border-radius: 50%; }
        .skel-line { height: 14px; margin-bottom: 10px; width: 100%; }
        .skel-media { height: 250px; border-radius: 15px; margin-top: 15px; }
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
    if (!currentUser) window.location.href = "login.html";
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
    // THE FIX: Safely destroy the correct "currentUser" badge!
    localStorage.removeItem("currentUser"); 
    window.location.href = "login.html"; 
}

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
            fetch(`${API_BASE}/api/editPost`, {
                method: 'POST',
                body: JSON.stringify({ username: currentUser, postId: postId.toString(), content: newContent })
            })
            .then(res => res.text())
            .then(data => {
                if (data === "SUCCESS") {
                    showToast("Post updated!");
                    location.reload(); 
                }
            });
        }
    });
}

function deletePost(postId) {
    openConfirmModal("Are you sure you want to permanently delete this post?", () => {
        fetch(`${API_BASE}/api/deletePost`, {
            method: 'POST',
            body: JSON.stringify({ username: currentUser, postId: postId.toString() })
        })
        .then(res => res.text())
        .then(data => {
            if (data === "SUCCESS") {
                showToast("Post deleted.");
                location.reload();
            }
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
        let currentLikes = parseInt(element.textContent.replace(/[^0-9]/g, '')) || 0;
        
        if (data === "LIKED") {
            element.innerHTML = `<span class="material-icons" style="font-size: 14px;">favorite</span> ${currentLikes + 1}`;
            element.style.color = "#ff3366";
            element.style.background = "rgba(255, 51, 102, 0.1)";
        } else if (data === "UNLIKED") {
            element.innerHTML = `<span class="material-icons" style="font-size: 14px;">favorite</span> ${currentLikes - 1}`;
            element.style.color = "";
            element.style.background = "";
        }
    });
}

function editComment(commentId, currentContent, postId) {
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
                    loadComments(postId);
                }
            });
        }
    });
}

function deleteComment(commentId, postId) {
    openConfirmModal("Are you sure you want to delete this comment?", () => {
        fetch(`${API_BASE}/api/deleteComment`, {
            method: 'POST',
            body: JSON.stringify({ username: currentUser, commentId: commentId.toString() })
        })
        .then(res => res.text())
        .then(data => {
            if (data === "SUCCESS") {
                showToast("Comment deleted.");
                loadComments(postId);
            }
        });
    });
}

function toggleComments(postId) {
    const commentSection = document.getElementById(`comments-${postId}`);
    if (!commentSection) return;

    if (commentSection.style.display === "none" || commentSection.style.display === "") {
        commentSection.style.display = "block";
        loadComments(postId); 
    } else {
        commentSection.style.display = "none";
    }
}

function loadComments(postId) {
    const commentList = document.getElementById(`comment-list-${postId}`);
    if (!commentList) return;

    commentList.innerHTML = "<p style='color: gray; font-size: 14px; text-align: center;'>Loading...</p>";

    fetch(`${API_BASE}/api/getComments`, {
        method: 'POST',
        body: JSON.stringify({ postId: postId.toString(), currentUser: currentUser })
    })
    .then(response => response.json())
    .then(comments => {
        commentList.innerHTML = comments.length === 0 ? "<p style='text-align:center; color:gray;'>No comments yet.</p>" : "";
        comments.forEach(comment => {
            let commentFollowBtn = "";
            if (comment.username !== currentUser) {
                const btnStyle = comment.isFollowing ? 'background: rgba(255, 51, 102, 0.1); color: #ff3366; border-color: rgba(255, 51, 102, 0.3);' : '';
                const btnText = comment.isFollowing ? 'Unfollow' : 'Follow';
                commentFollowBtn = `<button class="media-control-btn" style="margin-left: 10px; padding: 2px 6px; font-size: 10px; ${btnStyle}" onclick="feedFollowUser(this, '${comment.username}')">${btnText}</button>`;
            }

            const editedTag = comment.isEdited ? `<span style="font-size: 11px; color: #6a6680; font-style: italic;"> (edited)</span>` : "";
            const commentLikeStyle = comment.isLiked ? 'color: #ff3366; background: rgba(255, 51, 102, 0.1);' : '';

            let editDeleteHTML = "";
            if (comment.username === currentUser) {
                editDeleteHTML = `
                    <span onclick="editComment(${comment.id}, '${comment.content.replace(/'/g, "\\'")}', ${postId})" style="cursor: pointer; color: #a09eb5; margin-left: 10px; transition: 0.2s;" title="Edit Comment">
                        <span class="material-icons" style="font-size: 14px;">edit</span>
                    </span>
                    <span onclick="deleteComment(${comment.id}, ${postId})" style="cursor: pointer; color: #ff3366; margin-left: 5px; transition: 0.2s;" title="Delete Comment">
                        <span class="material-icons" style="font-size: 14px;">delete</span>
                    </span>
                `;
            }

            commentList.innerHTML += `
                <div style="background: rgba(255,255,255,0.05); padding: 10px; border-radius: 8px; margin-bottom: 8px; border-left: 3px solid #00e676;">
                    <div style="display: flex; align-items: center; gap: 4px;">
                        <a href="profile.html?user=${comment.username}" style="text-decoration: none;"><strong style="color: #00e676; font-size: 14px;">${comment.username}</strong></a>
                        ${editedTag}
                        ${editDeleteHTML}
                        ${commentFollowBtn}
                        <span style="font-size: 11px; color: gray; margin-left: auto;">${comment.timestamp}</span>
                    </div>
                    <p style="margin: 5px 0; font-size: 14px; color: #e0e0e0;">${comment.content}</p>
                    
                    <div style="display: flex; gap: 15px; margin-top: 8px; align-items: center; color: #a09eb5;">
                        <span onclick="toggleCommentLike(this, ${comment.id})" class="like-btn" style="cursor: pointer; display: flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px; ${commentLikeStyle}">
                            <span class="material-icons" style="font-size: 14px;">favorite</span> ${comment.likes}
                        </span>
                        
                        <span class="repost-btn" onclick="openRepostMenu(${postId}, false, ${comment.id})" style="cursor: pointer; display: flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px;">
                            <span class="material-icons" style="font-size: 14px;">repeat</span>
                        </span>
                        
                        <span class="quote-btn" onclick="openCommentQuotesModal(${comment.id})" style="cursor: pointer; display: flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px;">
                            <span class="material-icons" style="font-size: 14px;">format_quote</span>
                        </span>

                        <span style="cursor: pointer; display: flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 12px;" onclick="replyToComment(${postId}, '${comment.username}')">
                            <span class="material-icons" style="font-size: 14px;">reply</span> Reply
                        </span>
                    </div>
                </div>`;
        });
    })
    .catch(error => console.error("Error loading comments:", error));
}

function submitComment(postId) {
    const inputField = document.getElementById(`comment-input-${postId}`);
    if (!inputField) return;
    const content = inputField.value;

    if (content.trim() !== "") {
        fetch(`${API_BASE}/api/addComment`, {
            method: 'POST',
            body: JSON.stringify({ username: currentUser, postId: postId.toString(), content: content })
        })
        .then(response => response.text())
        .then(data => {
            if (data === "SUCCESS") {
                inputField.value = ""; 
                loadComments(postId); 
            }
        });
    }
}

function replyToComment(postId, targetUsername) {
    const inputField = document.getElementById(`comment-input-${postId}`);
    if (inputField) {
        inputField.value = `@${targetUsername} `;
        inputField.focus();
    }
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

    document.getElementById('repostConfirm').onclick = () => { executeRepost(postId, commentId); overlay.remove(); };
    document.getElementById('quoteConfirm').onclick = () => { openQuoteEditor(postId, commentId); overlay.remove(); };
}

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
        executeQuote(postId, textArea.value, quoteMediaBase64, commentId);
        overlay.remove();
    };
}

function openCommentQuotesModal(commentId) {
    let existingModal = document.getElementById('commentQuotesModalOverlay');
    if (existingModal) existingModal.remove();

    const overlay = document.createElement('div');
    overlay.id = 'commentQuotesModalOverlay';
    overlay.className = 'modal-overlay';
    overlay.style.zIndex = '5000';
    overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };

    const menuCard = document.createElement('div');
    menuCard.className = 'modal-card';
    menuCard.style.maxWidth = '450px';
    menuCard.style.width = '90%';
    menuCard.style.maxHeight = '80vh';
    menuCard.style.display = 'flex';
    menuCard.style.flexDirection = 'column';

    menuCard.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
            <h3 style="margin: 0; color: white;">Quoted By</h3>
            <button id="closeCommentQuotesBtn" style="background: transparent; border: none; color: #ff3366; cursor: pointer; display: flex; align-items: center;"><span class="material-icons">close</span></button>
        </div>
        <div id="commentQuotesListContainer" style="overflow-y: auto; flex: 1; padding-right: 10px;">
            <p style="text-align: center; color: #00e676;">Loading quotes...</p>
        </div>
    `;

    overlay.appendChild(menuCard);
    document.body.appendChild(overlay);

    document.getElementById('closeCommentQuotesBtn').onclick = () => overlay.remove();

    const container = document.getElementById('commentQuotesListContainer');

    fetch(`${API_BASE}/api/getCommentQuotes`, {
        method: 'POST',
        body: JSON.stringify({ commentId: commentId.toString(), currentUser: currentUser })
    })
    .then(res => res.json())
    .then(posts => {
        if (posts.length === 0) {
            container.innerHTML = "<p style='text-align: center; color: gray;'>No quotes yet.</p>";
            return;
        }
        
        let quotesHTML = "";
        posts.forEach(post => {
            let mediaHTML = "";
            if (post.media && post.media !== "") {
                mediaHTML = `<img src="${post.media}" style="width: 100%; border-radius: 10px; margin-top: 10px; object-fit: cover;">`;
            }
            quotesHTML += `
                <div style="background: rgba(255,255,255,0.05); padding: 15px; border-radius: 12px; margin-bottom: 10px; border-left: 3px solid #00e676; cursor: pointer; transition: 0.2s;" onclick="window.location.href='post.html?id=${post.id}'">
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <img src="${post.avatar}" style="width: 35px; height: 35px; border-radius: 50%; object-fit: cover;">
                        <strong style="color: white;">${post.username}</strong>
                        <span style="color: gray; font-size: 12px; margin-left: auto;">${post.timestamp}</span>
                    </div>
                    <p style="margin-top: 10px; color: #e0e0e0; font-size: 14px;">${post.content}</p>
                    ${mediaHTML}
                </div>
            `;
        });
        container.innerHTML = quotesHTML;
    })
    .catch(err => {
        container.innerHTML = "<p style='text-align: center; color: #ff3366;'>Error loading quotes.</p>";
    });
}

function executeRepost(postId, commentId = null) {
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
            showToast("Success!");
            if(typeof loadFeed === "function") loadFeed(); 
        }
    });
}

function executeQuote(postId, textContent, mediaBase64 = "", commentId = null) {
    fetch(`${API_BASE}/api/createPost`, {
        method: 'POST',
        body: JSON.stringify({ 
            username: currentUser, 
            content: textContent, 
            media: mediaBase64, 
            parentPostId: postId,
            parentCommentId: commentId 
        })
    })
    .then(response => response.text())
    .then(data => {
        if (data === "SUCCESS") {
            showToast("Quote Posted!");
            if(typeof loadFeed === "function") loadFeed(); 
        }
    });
}

function buildQuoteBox(parentPost) {
    if (!parentPost) return ""; 
    
    let pMediaHTML = "";
    if (parentPost.media && parentPost.media !== "") {
        if (parentPost.media.toLowerCase().endsWith(".mp4") || parentPost.media.startsWith("data:video")) {
            pMediaHTML = `<video src="${parentPost.media}" controls onclick="event.stopPropagation();" style="width: 100%; max-height: 250px; border-radius: 8px; margin-top: 10px; background: #000;"></video>`;
        } else {
            pMediaHTML = `<img src="${parentPost.media}" style="width: 100%; max-height: 250px; border-radius: 8px; margin-top: 10px; object-fit: cover;">`;
        }
    }

    const pEditedTag = parentPost.isEdited ? `<span style="font-size: 11px; color: #6a6680; font-style: italic;"> (edited)</span>` : "";
    const linkTarget = parentPost.isComment ? `post.html?id=${parentPost.postId}` : `post.html?id=${parentPost.id}`;

    return `
    <div class="embedded-quote" onclick="event.stopPropagation(); window.location.href='${linkTarget}'" style="border: 1px solid #334155; border-radius: 12px; padding: 12px; margin-top: 10px; background: rgba(255,255,255,0.02); cursor: pointer; transition: background 0.2s;">
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
            <img src="${parentPost.avatar}" style="width: 20px; height: 20px; border-radius: 50%; object-fit: cover;">
            <strong style="font-size: 13px; color: white;">${parentPost.username}</strong>
            ${pEditedTag}
            <span style="font-size: 11px; color: gray; margin-left: auto;">${parentPost.timestamp}</span>
        </div>
        <div style="font-size: 14px; color: #e0e0e0; line-height: 1.4;">${parentPost.content}</div>
        ${pMediaHTML}
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

/* ========================================== */
/* --- GLOBAL SCROLL MEMORY ENGINE            */
/* ========================================== */
// Saves your exact pixel depth before you click a post
function saveScrollPosition(pageIdentifier = 'general') {
    sessionStorage.setItem(pageIdentifier + 'Scroll', window.scrollY || document.documentElement.scrollTop);
}

// Forces the browser to jump back down after it finishes loading the feed
function restoreScrollPosition(pageIdentifier = 'general') {
    setTimeout(() => {
        const savedScroll = sessionStorage.getItem(pageIdentifier + 'Scroll');
        if (savedScroll) {
            window.scrollTo(0, parseInt(savedScroll));
            sessionStorage.removeItem(pageIdentifier + 'Scroll');
        }
    }, 100); // 100ms delay ensures the new HTML is fully painted before scrolling
}

/* ========================================= */
/* --- THE SOCIAL TEXT PARSER (REGEX) ---    */
/* ========================================= */
function parseSocialText(text) {
    if (!text) return "";
    
    // 1. [Mentions Parser]: Finds @username 
    let html = text.replace(/@([\w_]+)/g, `<a href="profile.html?user=$1" style="color: rgb(0, 168, 255); text-decoration: none; font-weight: bold;" onclick="event.stopPropagation();">@$1</a>`);
    
    // 2. [Hashtag Parser]: Finds #trend 
    html = html.replace(/#([\w_]+)/g, `<a href="search.html?q=%23$1" style="color: rgb(0, 230, 118); text-decoration: none; font-weight: bold;" onclick="event.stopPropagation();">#$1</a>`);
    
    return html;
}

function closeLightbox() {
    const overlay = document.getElementById('customLightboxOverlay');
    if (overlay) overlay.style.display = 'none';
}

// [Master Hack]: Overriding the browser's native fullscreen
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

checkNotifications();
checkUnreadMessages(); 
highlightActiveNav();
setInterval(checkNotifications, 30000);
setInterval(checkUnreadMessages, 1000);