# Repair request demo images

Placeholder SVGs that customers can attach to a repair request
(`imageUrls: ["/demo-images/repairs/phone-cracked-screen.svg"]`).
ReLoop has no image upload/hosting yet; requests accept these paths or external `https://` URLs.
Served by repair-service at `http://localhost:8083/demo-images/repairs/<name>.svg`
and listed by `GET /api/repair-requests/demo-images`.
