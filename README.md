# Getting Started
This project has been split into separate repositories:
- **Backend API:** https://github.com/yourusername/cold-email-backend
- **Frontend Web App:** https://github.com/yourusername/cold-email-frontend

## Quick Start

1. Clone and setup backend
2. Clone and setup frontend
3. Setup Gmail API 
4. Run both applications

See individual repository READMEs for detailed instructions.
" > README.md   

## Gmail API Setup

### Step 1: Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Gmail API for your project

### Step 2: Create Credentials

1. Go to "Credentials" in the API & Services section
2. Click "Create Credentials" → "OAuth 2.0 Client IDs"
3. Choose "Web application" as Application Type
4. Make a note of Google CLIENT SECRET KEY
5. Add Authorized redirect URI as "http://localhost:8080/api/auth/callback"

### Step 3: Configure OAuth Consent Screen

1. Go to "OAuth consent screen"
2. Add your email address as a test user as we would be doing this in Testing (application is not published)
3. Add required scopes: `../auth/gmail.compose`


### Reference Documentation
For further reference, please consider the following sections:
* https://developers.google.com/identity/protocols/oauth2

# Create environment file
Edit .env with your Google OAuth credentials:
GOOGLE_OAUTH_CLIENT_ID=xxxx.apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=xxxx
GOOGLE_OAUTH_REDIRECT_URI=http://localhost:8080/api/auth/callback
FRONTEND_URL=http://localhost:3000
SPRING_PROFILES_ACTIVE=local


# Run backend
mvn spring-boot:run

If the above command doesn't work on local, then try :
GOOGLE_OAUTH_CLIENT_ID=xxxx.apps.googleusercontent.com \
GOOGLE_OAUTH_CLIENT_SECRET=xxxx \
GOOGLE_OAUTH_REDIRECT_URI=http://localhost:8080/api/auth/callback \
SPRING_PROFILES_ACTIVE=local \
mvn spring-boot:run

# Test API
Quick check to see if your backend is working by testing API
* curl http://localhost:8080/api/email/health
* curl http://localhost:8080/api/email/user-info
* Note: If you are not authenticated in Gmail API, then it will show: No session found


# Full Integration Testing:
* Start backend service on port 8080
* Start frontend service on port 3000
* Test OAuth flow end-to-end
* Upload Excel file and generate emails
