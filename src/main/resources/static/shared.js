/* ========================================= */
/* --- SHARED SOCIAL CORE (V1.2 - CLEANED) --- */
/* ========================================= */

// Global variable for the logged-in user
const currentUser = localStorage.getItem("loggedInUser");

/* [THE BOUNCER] */
function checkSecurity() {
    if (!currentUser) {
        window.location.href = "login.html";
    }
}

/* --- LOGOUT LOGIC --- */
function openLogoutModal() {
    // [UI State Management]: We close the menu before showing the confirmation
    const menu = document.getElementById('fullMenu');
    if (menu) {
        menu.classList.remove('open');
    }

    const modal = document.getElementById('logoutModal');
    if (modal) {
        modal.style.display = 'flex';
    }
}

function closeLogoutModal() {
    const modal = document.getElementById('logoutModal');
    if (modal) modal.style.display = 'none';
}

function executeLogout() {
    localStorage.removeItem("loggedInUser"); 
    window.location.href = "login.html"; 
}

/* [THE LIKE ENGINE] */
function toggleLike(element, postId) {
    fetch('http://localhost:8080/api/toggleLike', {
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
        } else if (data === "UNLIKED") {
            likeCountSpan.textContent = currentLikes - 1;
            element.style.color = ""; 
        }
    })
    .catch(error => console.error("Like Error:", error));
}

/* [THE COMMENT ENGINE] */
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

    fetch('http://localhost:8080/api/getComments', {
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

            commentList.innerHTML += `
                <div style="background: rgba(255,255,255,0.05); padding: 10px; border-radius: 8px; margin-bottom: 8px; border-left: 3px solid #00e676;">
                    <div style="display: flex; align-items: center;">
                        <a href="profile.html?user=${comment.username}" style="text-decoration: none;"><strong style="color: #00e676; font-size: 14px;">${comment.username}</strong></a>
                        ${commentFollowBtn}
                        <span style="font-size: 11px; color: gray; margin-left: auto;">${comment.timestamp}</span>
                    </div>
                    <p style="margin: 5px 0; font-size: 14px; color: #e0e0e0;">${comment.content}</p>
                    <span style="font-size: 11px; color: #a09eb5; cursor: pointer;" onclick="replyToComment(${postId}, '${comment.username}')">↩️ Reply</span>
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
        fetch('http://localhost:8080/api/addComment', {
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

/* [FOLLOW ENGINE] */
function feedFollowUser(buttonElement, targetUsername) {
    fetch('http://localhost:8080/api/toggleFollow', {
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

/* --- UNIVERSAL MENU TOGGLE --- */
function toggleOverlayMenu() {
    const menu = document.getElementById('fullMenu');
    if (menu) {
        menu.classList.toggle('open');
    } else {
        console.error("Navigation Error: The overlay menu div ('fullMenu') is missing from this page.");
    }
}

/* --- FIXED SMART NAVIGATION HIGHLIGHTING --- */
function highlightActiveNav() {
    // [Path Normalization]: Standardizing the URL to handle root and filenames
    let path = window.location.pathname;
    if (path === "/" || path === "" || path.endsWith("/")) {
        path = "index.html";
    }
    
    // Get only the filename (e.g., 'profile.html') and ignore any [Query Parameters]
    const currentPage = path.split("/").pop();

    // Select all links in BOTH the bottom bar and the slide-out menu
    const navLinks = document.querySelectorAll('.bottom-nav a, .overlay-links a');

    navLinks.forEach(link => {
        const linkTarget = link.getAttribute('href');

        // Check if the link matches the page we are currently viewing
        if (currentPage === linkTarget) {
            link.classList.add('nav-link-active');
        } else {
            link.classList.remove('nav-link-active');
        }
    });
}

/* --- NOTIFICATION HEARTBEAT (SYNC) --- */
function checkNotifications() {
    if (!currentUser) return;

    fetch('http://localhost:8080/api/getNotifications', {
        method: 'POST',
        body: JSON.stringify({ username: currentUser })
    })
    .then(res => res.json())
    .then(data => {
        const unreadCount = data.filter(n => n.isRead === false || n.isRead === 0).length;
        const bell = document.getElementById('nav-bell');
        
        if (bell) {
            let bellContent = `<span class="material-icons">notifications</span>`;
            if (unreadCount > 0) {
                // [Dynamic Badge]: Injects the neon red notification bubble
                bellContent += `<span class="notification-badge">${unreadCount}</span>`;
            }
            bell.innerHTML = bellContent;
        }
    })
    .catch(err => console.error("Notification Sync Error:", err));
}

/* ========================================= */
/* --- BOOTSTRAP / INITIALIZATION --- */
/* ========================================= */

// Run these as soon as any page loads
checkNotifications();
highlightActiveNav();

// Set up the heartbeat to re-check notifications every 30 seconds
setInterval(checkNotifications, 30000);