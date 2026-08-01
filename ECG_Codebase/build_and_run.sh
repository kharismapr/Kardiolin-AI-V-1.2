#!/bin/bash

# Build and Run projectECG Java Application

cd "$(dirname "$0")"

echo "============================================"
echo "Building projectECG with NetBeans..."
echo "============================================"

# Build using Ant (NetBeans uses Ant)
ant -f build.xml clean build

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================"
    echo "✅ BUILD SUCCESSFUL!"
    echo "============================================"
    echo ""
    echo "To run the application in NetBeans:"
    echo "1. Open the project: File → Open Project → projectECG"
    echo "2. Build: Shift + F11"
    echo "3. Run: F6 or Run → Run Project"
    echo ""
    echo "The Python Flask server is running on port 5000"
    echo "You can now test the application!"
    echo ""
else
    echo ""
    echo "============================================"
    echo "❌ BUILD FAILED!"
    echo "============================================"
    echo ""
    echo "Please check the errors above."
    echo ""
fi
