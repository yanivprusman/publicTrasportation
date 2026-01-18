import requests

# Define the API endpoint and parameters
api_url = "http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/xml"
user_key = "YP719171"  # Replace with your user key
monitoring_ref = "32902"  # Replace with the desired station number

# Construct the full URL
params = {
    "Key": user_key,
    "MonitoringRef": monitoring_ref
}

# Make the API request
response = requests.get(api_url, params=params)

# Check the response status
if response.status_code == 200:
    print("API Response:")
    print(response.text)  # Print the XML response
else:
    print(f"Error: {response.status_code}")
    print(response.text)