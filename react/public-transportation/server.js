const express = require('express');
const axios = require('axios');
const cors = require('cors'); // Import the CORS middleware
const app = express();
const PORT = 5000;

app.use(cors()); // Enable CORS for all routes
app.use(express.json());

app.get('/api/directions', async (req, res) => {
  const { start, end } = req.query;
  const apiKey = 'YOUR_API_KEY'; // Replace with your actual API key

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

app.listen(PORT, () => {
  console.log(`Proxy server running on http://localhost:${PORT}`);
});
