require('dotenv').config({ path: __dirname + '/../../.env' }); // Load from root .env
const express = require('express');
const axios = require('axios');
const cors = require('cors'); // Import the CORS middleware
const app = express();
const PORT = process.env.PORT || 5000;

const path = require('path');

app.use(cors()); // Enable CORS for all routes
app.use(express.json());

// Serve static files from the build directory
app.use(express.static(path.join(__dirname, 'build')));

app.get('/api/directions', async (req, res) => {
  const { start, end } = req.query;
  const apiKey = process.env.ORS_API_KEY; // Loaded from .env

  try {
    const response = await axios.get(
      `https://api.openrouteservice.org/v2/directions/driving-car`,
      {
        params: {
          api_key: apiKey,
          start,
          end,
        },
      }
    );
    res.json(response.data);
  } catch (error) {
    console.error('Error fetching directions:', error.message);
    res.status(error.response?.status || 500).json({ error: 'Failed to fetch directions' });
  }
});

app.get(/(.*)/, (req, res) => {
  res.sendFile(path.join(__dirname, 'build', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Proxy server running on http://localhost:${PORT}`);
});
